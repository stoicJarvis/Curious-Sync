package curious.sync.services.redis.likesCache;

import static curious.sync.constants.Strings.LIKE_COUNT_CACHE;
import static curious.sync.constants.Strings.LIKE_STATE_CACHE;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import curious.sync.repositories.PostsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis-backed likes cache for two responsibilities:
 *
 * 1. LIKE STATE - Answers "has this user liked this post?"
 * 2. LIKE COUNT - Fast counter for getLikesCount() reads.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LikeStateCache {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PostsRepository postsRepository;

    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final Duration COUNT_TTL = Duration.ofMinutes(5);

    /**
     * Checks if a user has liked a specific post.
     */
    public boolean hasLiked(String postId, String userId) {
        String key = LIKE_STATE_CACHE + postId;
        Boolean member = redisTemplate.opsForSet().isMember(key, userId);
        return Boolean.TRUE.equals(member);
    }

    /**
     * Marks a user as having liked a specific post.
     */
    public void markLiked(String postId, String userId) {
        String key = LIKE_STATE_CACHE + postId;
        redisTemplate.opsForSet().add(key, userId);
        redisTemplate.expire(key, STATE_TTL);
    }

    /**
     * Marks a user as having unliked a specific post.
     */
    public void markUnliked(String postId, String userId) {
        String key = LIKE_STATE_CACHE + postId;
        redisTemplate.opsForSet().remove(key, userId);
        redisTemplate.expire(key, STATE_TTL);
    }

    /**
     * Returns cached count, or seeds from DB on cache miss.
     */
    public Long getCount(String postId) {
        String key = LIKE_COUNT_CACHE + postId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return Long.parseLong(cached.toString());
        }

        long dbCount = postsRepository.findById(postId)
                .map(post -> post.getTotal_likes())
                .orElse(0L);

        redisTemplate.opsForValue().set(key, dbCount, COUNT_TTL);
        log.debug("Seeded like count for post {} from DB: {}", postId, dbCount);

        return dbCount;
    }

    /**
     * Increments the like count for a specific post.
     */
    public void incrementCountBy(String postId, long delta) {
        String key = LIKE_COUNT_CACHE + postId;
        redisTemplate.opsForValue().increment(key, delta);
        redisTemplate.expire(key, COUNT_TTL);
    }

    /**
     * Decrements the like count for a specific post.
     */
    public void decrementCountBy(String postId, long delta) {
        String key = LIKE_COUNT_CACHE + postId;
        // Avoid going negative — get current value first
        Object current = redisTemplate.opsForValue().get(key);
        if (current != null) {
            long newVal = Math.max(0, Long.parseLong(current.toString()) - delta);
            redisTemplate.opsForValue().set(key, newVal, COUNT_TTL);
        }
    }
}
