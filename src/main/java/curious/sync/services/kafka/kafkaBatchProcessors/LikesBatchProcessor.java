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
public class LikesBatchProcessor {

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

        List<Like> allNewLikesToSave = new ArrayList<>();
        Map<String, Long> redisCountDeltas = new HashMap<>();

        for (Map.Entry<String, List<ReactionEvent>> entry : byPost.entrySet()) {
            String postId = entry.getKey();
            List<ReactionEvent> likesBatch = entry.getValue();

            List<String> userIds = likesBatch.stream()
                    .map(ReactionEvent::getUserId)
                    .distinct()
                    .collect(Collectors.toList());

            // Batch check Redis
            List<String> alreadyInRedis = likeStateCache.filterAlreadyLiked(postId, userIds);
            List<String> candidateLikes = userIds.stream()
                    .filter(userId -> !alreadyInRedis.contains(userId))
                    .collect(Collectors.toList());

            if (candidateLikes.isEmpty()) {
                log.debug("[like] All {} events for post {} already in Redis — skipped", userIds.size(), postId);
                continue;
            }

            // Batch check DB
            Set<String> existingInDb = likesRepository.findExistingLikerIds(postId, candidateLikes);
            List<String> newLikeUserIds = candidateLikes.stream()
                    .filter(userId -> !existingInDb.contains(userId))
                    .collect(Collectors.toList());

            if (newLikeUserIds.isEmpty()) {
                log.debug("[like] All Redis-miss events for post {} already exist in DB — skipped", postId);
                likeStateCache.markLikedBatch(postId, candidateLikes);
                continue;
            }

            // Prepare entities
            newLikeUserIds.forEach(userId -> {
                allNewLikesToSave.add(Like.builder()
                        .post(entityManager.getReference(Post.class, postId))
                        .user(entityManager.getReference(User.class, userId))
                        .build());
            });

            // Update DB counts
            long delta = newLikeUserIds.size();
            postsRepository.incrementLikesBy(postId, delta);
            
            redisCountDeltas.put(postId, delta);
            
            // Batch mark Redis state
            likeStateCache.markLikedBatch(postId, candidateLikes); 

            log.info("[like] post={} inserted={} skipped(redis)={} skipped(db)={}",
                    postId, delta, alreadyInRedis.size(), existingInDb.size());
        }

        // Bulk Save to DB
        if (!allNewLikesToSave.isEmpty()) {
            likesRepository.saveAll(allNewLikesToSave);
        }

        // Bulk update Redis counts
        if (!redisCountDeltas.isEmpty()) {
            likeStateCache.incrementLikesCount(redisCountDeltas);
        }
    }

    private void processUnlikes(List<ReactionEvent> unlikeEvents) {
        if (unlikeEvents == null || unlikeEvents.isEmpty()) {
            return;
        }

        Map<String, List<ReactionEvent>> byPost = unlikeEvents.stream()
                .collect(Collectors.groupingBy(ReactionEvent::getPostId));

        Map<String, Long> redisCountDeltas = new HashMap<>();

        for (Map.Entry<String, List<ReactionEvent>> entry : byPost.entrySet()) {
            String postId = entry.getKey();
            List<ReactionEvent> batch = entry.getValue();
            List<String> userIds = batch.stream().map(ReactionEvent::getUserId).collect(Collectors.toList());

            // Only process users that Redis confirms have actually liked (pipelined)
            List<String> confirmedLikers = likeStateCache.filterAlreadyLiked(postId, userIds);

            if (confirmedLikers.isEmpty()) {
                log.debug("[unlike] No confirmed likers for post {} — skipped", postId);
                continue;
            }

            // Delete each from DB (still one-by-one as deleteByUserIdAndPostId is specific)
            // But we can collect the total deleted for count updates
            long deletedCount = confirmedLikers.stream()
                    .mapToInt(userId -> likesRepository.deleteByUserIdAndPostId(userId, postId))
                    .sum();

            if (deletedCount > 0) {
                // Atomic DB decrement
                postsRepository.decrementLikesBy(postId, deletedCount);
                
                redisCountDeltas.put(postId, deletedCount);
                
                // Batch mark Redis as unliked
                likeStateCache.markUnlikedBatch(postId, confirmedLikers);
            }

            log.info("[unlike] post={} deleted={} skipped(redis)={}",
                    postId, deletedCount, batch.size() - confirmedLikers.size());
        }

        // Bulk update Redis counts
        if (!redisCountDeltas.isEmpty()) {
            likeStateCache.decrementLikesCount(redisCountDeltas);
        }
    }
}
