package curious.sync.services.kafka.kafkaBatchProcessors;

import static curious.sync.constants.Strings.LIKE_EVENT;
import static curious.sync.constants.Strings.UNLIKE_EVENT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import curious.sync.models.Events.ReactionEvent;
import curious.sync.repositories.Likes.LikesRepository;
import curious.sync.services.redis.likesCache.LikeStateCache;
import curious.sync.utils.KeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class LikesBatchProcessor {

    private final LikeStateCache likeStateCache;
    private final LikesRepository likesRepository;

    /**
     * Generic method to process reaction events.
     * Segregates like and unlike events and processes them separately.
     */
    @Transactional
    public void processReactionsBatch(List<ReactionEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        // If the same user likes + unlikes the same post in one batch, the LAST event wins.
        Map<String, ReactionEvent> uniqueReactions = new HashMap<>();
        for (ReactionEvent event : events) {
            uniqueReactions.put(KeyUtils.getUserPostEventKey(event.getUserId(), event.getPostId()), event);
        }

        List<ReactionEvent> likeEvents = new ArrayList<>();
        List<ReactionEvent> unlikeEvents = new ArrayList<>();
        for (ReactionEvent event : uniqueReactions.values()) {
            if (LIKE_EVENT.equals(event.getEventType())) {
                likeEvents.add(event);
            } else if (UNLIKE_EVENT.equals(event.getEventType())) {
                unlikeEvents.add(event);
            } else {
                log.error("Malformed event captured: {}", event);
            }
        }

        if (!likeEvents.isEmpty()) {
            processLikes(likeEvents);
        }

        if (!unlikeEvents.isEmpty()) {
            processUnlikes(unlikeEvents);
        }
    }

    /**
     * Filters like events by checking against Redis like state cache and then against DB like state.
     * Inserts the filtered like events into the database and updates the like count of the posts.
     * Synchronizes the filtered like events to Redis like state cache.
     * 
     * @param likeEvents List of like events to proces
     */
    private void processLikes(List<ReactionEvent> likeEvents) {
        if (likeEvents == null || likeEvents.isEmpty()) {
            return;
        }

        log.info("Processing batch of {} like events", likeEvents.size());

        List<ReactionEvent> filteredLikeEvents = filterDuplicateEvents(likeEvents);
        Map<String, List<ReactionEvent>> likesByPostId = mapEventsByPostId(filteredLikeEvents);

        Map<String, List<ReactionEvent>> filteredBatchOfLikes = likeStateCache.excludeExistingLikedEvents(likesByPostId);

        if (filteredBatchOfLikes.isEmpty()) return;
        
        Map<String, List<ReactionEvent>> finalBatchOfLikes = likesRepository.filterAlreadyLikedEvents(filteredBatchOfLikes);

        if (finalBatchOfLikes.isEmpty()) return;

        likesRepository.insertBatchOfLikeAndLikesCount(finalBatchOfLikes);

        likeStateCache.syncLikesBatchAndLikesCount(finalBatchOfLikes);

        int totalProcessed = finalBatchOfLikes.values().stream().mapToInt(List::size).sum();
        log.info("[likes-processor] Successfully processed {} like events across {} posts", totalProcessed, finalBatchOfLikes.size());
    }

    /**
     * Filters unlike events by checking against Redis like state cache and then against DB like state.
     * Inserts the filtered unlike events into the database and updates the like count of the posts.
     * Synchronizes the filtered unlike events to Redis like state cache.
     * @param unlikeEvents List of unlike events to process
     */
    private void processUnlikes(List<ReactionEvent> unlikeEvents) {
        if (unlikeEvents == null || unlikeEvents.isEmpty()) {
            return;
        }

        log.info("Processing batch of {} unlike events", unlikeEvents.size());

        List<ReactionEvent> filteredUnlikeEvents = filterDuplicateEvents(unlikeEvents);
        Map<String, List<ReactionEvent>> unlikesByPostId = mapEventsByPostId(filteredUnlikeEvents);

        Map<String, List<ReactionEvent>> filteredBatchOfUnlikes = likeStateCache.excludeExistingUnlikedEvents(unlikesByPostId);

        if (filteredBatchOfUnlikes.isEmpty()) return;
        
        Map<String, List<ReactionEvent>> finalBatchOfUnlikes = likesRepository.discardOrphanedUnlikes(filteredBatchOfUnlikes);

        if (finalBatchOfUnlikes.isEmpty()) return;

        likesRepository.deleteBatchOfUnlikeAndLikesCount(finalBatchOfUnlikes);

        likeStateCache.syncUnlikesBatchAndLikesCount(finalBatchOfUnlikes);

        int totalProcessed = finalBatchOfUnlikes.values().stream().mapToInt(List::size).sum();
        log.info("[unlikes-processor] Successfully processed {} unlike events across {} posts", totalProcessed, finalBatchOfUnlikes.size());
    }

    /**
     * if multiple events are there for same user and post, then only last event will be considered
     */
    private List<ReactionEvent> filterDuplicateEvents(List<ReactionEvent> events) {
        if (events == null || events.isEmpty()) {
            return new ArrayList<>();
        }

        return new ArrayList<>(events.stream()
                .collect(Collectors.toMap(
                        event -> KeyUtils.getUserPostEventKey(event.getUserId(), event.getPostId()),
                        event -> event,
                        (existing, incoming) -> incoming // Keep the latest event
                ))
                .values());
    }

    /**
     * Returns a map of reaction events keyed by post ID.
     */
    private Map<String, List<ReactionEvent>> mapEventsByPostId(List<ReactionEvent> reactionEvents) {

        if (reactionEvents == null || reactionEvents.isEmpty()) {
            return new HashMap<>();
        }

        return reactionEvents.stream()
                .collect(Collectors.groupingBy(ReactionEvent::getPostId));
    }
}
