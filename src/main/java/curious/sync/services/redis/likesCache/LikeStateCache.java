package curious.sync.services.redis.likesCache;

import static curious.sync.constants.Strings.LIKE_COUNT_CACHE;
import static curious.sync.constants.Strings.LIKE_STATE_CACHE;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.RedisCallback;
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

    private static final Duration LIKE_STATE_TTL = Duration.ofMinutes(10);
    private static final Duration LIKE_COUNT_TTL = Duration.ofMinutes(5);

    /**
     * Checks if a user has liked a specific post.
     */
    public boolean hasLiked(String postId, String userId) {
        String key = LIKE_STATE_CACHE + postId;
        Boolean hasLiked = redisTemplate.opsForSet().isMember(key, userId);
        return Boolean.TRUE.equals(hasLiked);
    }

    /**
     * Marks a user as having liked a specific post.
     */
    public void markLiked(String postId, String userId) {
        String key = LIKE_STATE_CACHE + postId;
        redisTemplate.opsForSet().add(key, userId);
        redisTemplate.expire(key, LIKE_STATE_TTL);
    }

    /**
     * Marks a user as having unliked a specific post.
     */
    public void markUnliked(String postId, String userId) {
        String key = LIKE_STATE_CACHE + postId;
        redisTemplate.opsForSet().remove(key, userId);
        redisTemplate.expire(key, LIKE_STATE_TTL);
    }

    /**
     * Checks multiple users for one post using a pipeline.
     * Returns a list of userIds that have ALREADY liked the post.
     */
    public List<String> filterAlreadyLiked(String postId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty())
            return List.of();

        String key = LIKE_STATE_CACHE + postId;
        List<Object> likedCandidates = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] keyBytes = key.getBytes();
            for (String userId : userIds) {
                connection.sIsMember(keyBytes, userId.getBytes());
            }
            return null;
        });

        List<String> alreadyLiked = new java.util.ArrayList<>();
        for (int i = 0; i < userIds.size(); i++) {
            if (Boolean.TRUE.equals(likedCandidates.get(i))) {
                alreadyLiked.add(userIds.get(i));
            }
        }

        return alreadyLiked;
    }

    /**
     * Marks multiple users as having liked a specific post using a pipeline.
     */
    public void markLikedBatch(String postId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty())
            return;

        String key = LIKE_STATE_CACHE + postId;
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] keyBytes = key.getBytes();
            for (String userId : userIds) {
                connection.sAdd(keyBytes, userId.getBytes());
            }
            connection.expire(keyBytes, LIKE_STATE_TTL.getSeconds());
            return null;
        });
    }

    /**
     * Marks multiple users as having unliked a specific post using a pipeline.
     */
    public void markUnlikedBatch(String postId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty())
            return;

        String key = LIKE_STATE_CACHE + postId;
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] keyBytes = key.getBytes();
            for (String userId : userIds) {
                connection.sRem(keyBytes, userId.getBytes());
            }
            connection.expire(keyBytes, LIKE_STATE_TTL.getSeconds());
            return null;
        });
    }

    /**
     * Returns cached count, or seeds from DB on cache miss.
     */
    public Long getLikesCount(String postId) {
        String key = LIKE_COUNT_CACHE + postId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return Long.parseLong(cached.toString());
        }

        long likesCount = postsRepository.findById(postId)
                .map(post -> post.getTotal_likes())
                .orElse(0L);

        redisTemplate.opsForValue().set(key, likesCount, LIKE_COUNT_TTL);
        log.debug("Seeded like count for post {} from DB: {}", postId, likesCount);

        return likesCount;
    }

    /**
     * Increments the like count for multiple posts using a pipeline.
     */
    public void incrementLikesCount(Map<String, Long> postDeltas) {
        if (postDeltas == null || postDeltas.isEmpty())
            return;

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            postDeltas.forEach((postId, delta) -> {
                String key = LIKE_COUNT_CACHE + postId;
                connection.incrBy(key.getBytes(), delta);
                connection.expire(key.getBytes(), LIKE_COUNT_TTL.getSeconds());
            });
            return null;
        });
    }

    /**
     * Decrements the like count for multiple posts using a pipeline.
     */
    public void decrementLikesCount(Map<String, Long> postDeltas) {
        if (postDeltas == null || postDeltas.isEmpty())
            return;

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            postDeltas.forEach((postId, delta) -> {
                String key = LIKE_COUNT_CACHE + postId;
                connection.decrBy(key.getBytes(), delta);
                connection.expire(key.getBytes(), LIKE_COUNT_TTL.getSeconds());
            });
            return null;
        });
    }
}
