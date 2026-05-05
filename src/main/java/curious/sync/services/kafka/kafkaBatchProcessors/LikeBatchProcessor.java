package curious.sync.services.kafka.kafkaBatchProcessors;

import static curious.sync.constants.Strings.LIKE_EVENT;
import static curious.sync.constants.Strings.UNLIKE_EVENT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import curious.sync.models.Events.ReactionEvent;
import curious.sync.models.core.Like;
import curious.sync.models.core.Post;
import curious.sync.models.core.User;
import curious.sync.repositories.LikesRepository;
import curious.sync.repositories.PostsRepository;
import curious.sync.services.redis.likesCache.LikeStateCache;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class LikeBatchProcessor {

    private final LikesRepository likesRepository;
    private final PostsRepository postsRepository;
    private final LikeStateCache likeStateCache;
    private final EntityManager entityManager;

    /**
     * Generic method to process reaction events.
     * Segregates like and unlike events and processes them separately.
     */
    @Transactional
    public void processReactionsBatch(List<ReactionEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        log.info("Processing batch of {} reaction events", events.size());

        // If the same user likes + unlikes the same post in one batch, the LAST event wins.
        Map<String, ReactionEvent> uniqueReactions = new HashMap<>();
        for (ReactionEvent event : events) {
            uniqueReactions.put(event.getUserId() + ":" + event.getPostId(), event);
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

    private void processLikes(List<ReactionEvent> likeEvents) {

        if (likeEvents == null || likeEvents.isEmpty()) {
            return;
        }

        Map<String, List<ReactionEvent>> byPost = likeEvents.stream()
                .collect(Collectors.groupingBy(ReactionEvent::getPostId));

        for (Map.Entry<String, List<ReactionEvent>> entry : byPost.entrySet()) {
            String postId = entry.getKey();
            List<ReactionEvent> likesBatch = entry.getValue();

            List<String> userIds = likesBatch.stream()
                    .map(ReactionEvent::getUserId)
                    .distinct()
                    .collect(Collectors.toList());

            List<String> candidateLikes = userIds.stream()
                    .filter(userId -> !likeStateCache.hasLiked(postId, userId))
                    .collect(Collectors.toList());

            if (candidateLikes.isEmpty()) {
                log.debug("[like] All {} events for post {} already in Redis — skipped", userIds.size(), postId);
                continue;
            }

            Set<String> existingInDb = likesRepository.findExistingLikerIds(postId, candidateLikes);
            List<String> newLikeEvents = candidateLikes.stream()
                    .filter(userId -> !existingInDb.contains(userId))
                    .collect(Collectors.toList());

            if (newLikeEvents.isEmpty()) {
                log.debug("[like] All Redis-miss events for post {} already exist in DB — skipped", postId);
                continue;
            }

            List<Like> newLikes = newLikeEvents.stream()
                    .map(userId -> Like.builder()
                            .post(entityManager.getReference(Post.class, postId))
                            .user(entityManager.getReference(User.class, userId))
                            .build())
                    .collect(Collectors.toList());
            likesRepository.saveAll(newLikes);

            long delta = newLikeEvents.size();

            postsRepository.incrementLikesBy(postId, delta);

            likeStateCache.incrementCountBy(postId, delta);
            newLikeEvents.forEach(userId -> likeStateCache.markLiked(postId, userId));

            log.info("[like] post={} inserted={} skipped(redis)={} skipped(db)={}",
                    postId, delta, userIds.size() - candidateLikes.size(), candidateLikes.size() - candidateLikes.size());
        }
    }

    private void processUnlikes(List<ReactionEvent> unlikEvents) {

        if (unlikEvents == null || unlikEvents.isEmpty()) {
            return;
        }

        Map<String, List<ReactionEvent>> byPost = unlikEvents.stream()
                .collect(Collectors.groupingBy(ReactionEvent::getPostId));

        for (Map.Entry<String, List<ReactionEvent>> entry : byPost.entrySet()) {
            String postId = entry.getKey();
            List<ReactionEvent> batch = entry.getValue();

            // Only process users that Redis confirms have actually liked
            List<String> confirmedLikers = batch.stream()
                    .map(ReactionEvent::getUserId)
                    .filter(userId -> likeStateCache.hasLiked(postId, userId))
                    .collect(Collectors.toList());

            if (confirmedLikers.isEmpty()) {
                log.debug("[unlike] No confirmed likers for post {} — skipped", postId);
                continue;
            }

            // Delete each (indexed lookup on unique constraint — very fast)
            long deleted = confirmedLikers.stream()
                    .mapToInt(userId -> likesRepository.deleteByUserIdAndPostId(userId, postId))
                    .sum();

            if (deleted > 0) {
                postsRepository.decrementLikesBy(postId, deleted);
                likeStateCache.decrementCountBy(postId, deleted);
                confirmedLikers.forEach(userId -> likeStateCache.markUnliked(postId, userId));
            }

            log.info("[unlike] post={} deleted={} skipped(redis)={}",
                    postId, deleted, batch.size() - confirmedLikers.size());
        }
    }
}
