package curious.sync.services.core.Likes;

import static curious.sync.constants.Strings.LIKE_EVENT;

import java.time.Duration;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import curious.sync.configurations.RequestCoalescer.RequestCoalescer;
import curious.sync.constants.ReactionAction;
import curious.sync.models.Events.ReactionEvent;
import curious.sync.repositories.scyllaDb.Likes.LikesCountRepository;
import curious.sync.services.kafka.kafkaEventProducers.ReactionEventProducer;
import curious.sync.utils.KeyUtils;
import curious.sync.utils.SnowflakeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Write-behind likes: Redis is the hot path (idempotency + optimistic count),
 * Kafka carries the durable event, ScyllaDB is the source of truth on cache miss.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LikesService {

    private final StringRedisTemplate redisTemplate;
    private final ReactionEventProducer reactionEventProducer;
    private final LikesCountRepository scyllaRepository;

    private final RequestCoalescer<Long> likesCountCoalescer = new RequestCoalescer<>("likes-count");

    /**
     * Synchronously records a like in Redis and publishes a Kafka event.
     * Duplicate likes are no-ops. Redis is rolled back if Kafka publish fails.
     */
    public Map<String, Object> likePost(Long userId, Long postId) {
        String likedByKey = KeyUtils.likedByKey(postId);
        String countKey = KeyUtils.likesCountKey(postId);
        String userMember = String.valueOf(userId);

        Long added = redisTemplate.opsForSet().add(likedByKey, userMember);
        if (added == null || added == 0L) {
            log.debug("Idempotent like skipped — user={} already liked post={}", userId, postId);
            return Map.of("action", LIKE_EVENT, "postId", postId);
        }

        try {
            redisTemplate.opsForValue().increment(countKey);
        } catch (RuntimeException incrementFailure) {
            safeSrem(likedByKey, userMember, userId, postId);
            throw incrementFailure;
        }

        try {
            ReactionEvent event = ReactionEvent.builder()
                    .postId(postId)
                    .userId(userId)
                    .reactionAction(ReactionAction.LIKE)
                    .build();

            reactionEventProducer.sendLikeEvent(event);
        } catch (RuntimeException e) {
            rollbackRedisLike(likedByKey, countKey, userMember, userId, postId);
            throw e;
        }

        return Map.of("action", LIKE_EVENT, "postId", postId);
    }

    /**
     * Cache-aside read of a post's like count.
     */
    public long getLikesCount(Long postId) {
        String countKey = KeyUtils.likesCountKey(postId);
        String cached = redisTemplate.opsForValue().get(countKey);

        if (cached != null) {
            return Long.parseLong(cached);
        }

        return likesCountCoalescer.coalesce(countKey, () -> loadAndCacheCount(postId, countKey));
    }

    private long loadAndCacheCount(Long postId, String countKey) {
        String cached = redisTemplate.opsForValue().get(countKey);

        if (cached != null) {
            return Long.parseLong(cached);
        }

        long count = scyllaRepository.getLikesCount(postId);

        Duration ttl = SnowflakeUtils.calculateDynamicTtl(postId);
        redisTemplate.opsForValue().set(countKey, String.valueOf(count), ttl);

        log.debug("Cached likes_count for post={} count={} ttl={}", postId, count, ttl);

        return count;
    }

    private void rollbackRedisLike(
            String likedByKey,
            String countKey,
            String userMember,
            Long userId,
            Long postId) {
        try {
            redisTemplate.opsForSet().remove(likedByKey, userMember);
            redisTemplate.opsForValue().decrement(countKey);
            log.warn("Rolled back Redis like — user={} post={}", userId, postId);
        } catch (RuntimeException rollbackFailure) {
            log.error(
                    "Redis rollback failed after Kafka error — user={} post={}. Manual repair may be required",
                    userId,
                    postId,
                    rollbackFailure);
        }
    }

    private void safeSrem(String likedByKey, String userMember, Long userId, Long postId) {
        try {
            redisTemplate.opsForSet().remove(likedByKey, userMember);
        } catch (RuntimeException rollbackFailure) {
            log.error(
                    "Failed to undo SADD after INCR failure — user={} post={}",
                    userId,
                    postId,
                    rollbackFailure);
        }
    }
}

