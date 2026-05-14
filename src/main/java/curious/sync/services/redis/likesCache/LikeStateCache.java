package curious.sync.services.redis.likesCache;

import static curious.sync.constants.Strings.LIKE_COUNT_CACHE;
import static curious.sync.constants.Strings.LIKE_STATE_CACHE;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import curious.sync.models.Events.ReactionEvent;
import curious.sync.repositories.PostsRepository;
import curious.sync.utils.KeyUtils;
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
        String key = KeyUtils.getRedisStateKey(LIKE_STATE_CACHE, postId);
        Boolean hasLiked = redisTemplate.opsForSet().isMember(key, userId);
        return Boolean.TRUE.equals(hasLiked);
    }

    /**
     * Marks a user as having liked a specific post.
     */
    public void markLiked(String postId, String userId) {
        String key = KeyUtils.getRedisStateKey(LIKE_STATE_CACHE, postId);
        redisTemplate.opsForSet().add(key, userId);
        redisTemplate.expire(key, LIKE_STATE_TTL);
    }

    /**
     * Checks multiple users for one post using a pipeline.
     * Returns a list of userIds that have ALREADY liked the post.
     */
    public List<String> filterAlreadyLiked(String postId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty())
            return List.of();

        String key = KeyUtils.getRedisStateKey(LIKE_STATE_CACHE, postId);
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
     * Returns cached count, or seeds from DB on cache miss.
     */
    public Long getLikesCount(String postId) {
        String key = KeyUtils.getRedisCountKey(LIKE_COUNT_CACHE, postId);
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
     * Filters out events for posts that have already been liked by the users.
     * 
     * @return A map of post IDs to lists of reaction events, with already liked
     *         events removed.
     */
    public Map<String, List<ReactionEvent>> excludeExistingLikedEvents(Map<String, List<ReactionEvent>> eventsByPostId) {
        if (eventsByPostId == null || eventsByPostId.isEmpty()) {
            return eventsByPostId;
        }

        List<String> orderedPostIds = new ArrayList<>(eventsByPostId.keySet());

        List<Object> pipelineResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String postId : orderedPostIds) {
                String redisKey = KeyUtils.getRedisStateKey(LIKE_STATE_CACHE, postId);

                byte[][] userIds = eventsByPostId.get(postId).stream()
                        .map(event -> event.getUserId().getBytes(StandardCharsets.UTF_8))
                        .toArray(byte[][]::new);

                connection.sMIsMember(redisKey.getBytes(StandardCharsets.UTF_8), userIds);
            }
            return null;
        });

        Set<String> eventsToRemove = new HashSet<>();

        for (int postIdIterator = 0; postIdIterator < orderedPostIds.size(); postIdIterator++) {
            String postId = orderedPostIds.get(postIdIterator);
            List<ReactionEvent> eventsForPost = eventsByPostId.get(postId);

            @SuppressWarnings("unchecked")
            List<Boolean> membershipResults = (List<Boolean>) pipelineResults.get(postIdIterator);

            for (int eventIterator = 0; eventIterator < eventsForPost.size(); eventIterator++) {
                if (Boolean.TRUE.equals(membershipResults.get(eventIterator))) {
                    ReactionEvent event = eventsForPost.get(eventIterator);
                    eventsToRemove.add(KeyUtils.generateUserPostKey(event.getUserId(), event.getPostId()));
                }
            }
        }

        eventsByPostId.values().forEach(eventList -> eventList.removeIf(
                event -> eventsToRemove.contains(KeyUtils.generateUserPostKey(event.getUserId(), event.getPostId()))));

        eventsByPostId.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        return eventsByPostId;
    }

    /**
     * Synchronizes likes to Redis in a single pipeline trip.
     * Updates both the state (sets) and the likes count (counters).
     */
    public void syncLikesBatchAndLikesCount(Map<String, List<ReactionEvent>> likesEvent, Map<String, Long> postsDelta) {
        if (likesEvent == null || likesEvent.isEmpty()) return;

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            likesEvent.forEach((postId, events) -> {
                String stateKey = KeyUtils.getRedisStateKey(LIKE_STATE_CACHE, postId);
                byte[] stateKeyBytes = stateKey.getBytes(StandardCharsets.UTF_8);
                for (ReactionEvent event : events) {
                    connection.sAdd(stateKeyBytes, event.getUserId().getBytes(StandardCharsets.UTF_8));
                }
                connection.expire(stateKeyBytes, LIKE_STATE_TTL.getSeconds());

                Long insertedCount = postsDelta.getOrDefault(postId, 0L);
                if (insertedCount > 0) {
                    String countKey = KeyUtils.getRedisCountKey(LIKE_COUNT_CACHE, postId);
                    byte[] countKeyBytes = countKey.getBytes(StandardCharsets.UTF_8);
                    connection.incrBy(countKeyBytes, insertedCount);
                    connection.expire(countKeyBytes, LIKE_COUNT_TTL.getSeconds());
                }
            });
            return null;
        });
    }

    public Map<String, List<ReactionEvent>> excludeExistingUnlikedEvents(Map<String, List<ReactionEvent>> eventsByPostId) {
        if (eventsByPostId == null || eventsByPostId.isEmpty()) {
            return eventsByPostId;
        }

        List<String> orderedPostIds = new ArrayList<>(eventsByPostId.keySet());

        List<Object> pipelineResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String postId : orderedPostIds) {
                String redisKey = KeyUtils.getRedisStateKey(LIKE_STATE_CACHE, postId);

                byte[][] userIds = eventsByPostId.get(postId).stream()
                        .map(event -> event.getUserId().getBytes(StandardCharsets.UTF_8))
                        .toArray(byte[][]::new);

                connection.sMIsMember(redisKey.getBytes(StandardCharsets.UTF_8), userIds);
            }
            return null;
        });

        Set<String> eventsToRemove = new HashSet<>();

        for (int postIdIterator = 0; postIdIterator < orderedPostIds.size(); postIdIterator++) {
            String postId = orderedPostIds.get(postIdIterator);
            List<ReactionEvent> eventsForPost = eventsByPostId.get(postId);

            @SuppressWarnings("unchecked")
            List<Boolean> membershipResults = (List<Boolean>) pipelineResults.get(postIdIterator);

            for (int eventIterator = 0; eventIterator < eventsForPost.size(); eventIterator++) {
                if (Boolean.FALSE.equals(membershipResults.get(eventIterator))) {
                    ReactionEvent event = eventsForPost.get(eventIterator);
                    eventsToRemove.add(KeyUtils.generateUserPostKey(event.getUserId(), event.getPostId()));
                }
            }
        }

        eventsByPostId.values().forEach(eventList -> eventList.removeIf(
                event -> eventsToRemove.contains(KeyUtils.generateUserPostKey(event.getUserId(), event.getPostId()))));

        eventsByPostId.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        return eventsByPostId;
    }

    public void syncUnlikesBatchAndLikesCount(Map<String, List<ReactionEvent>> unlikesEvent, Map<String, Long> postsDelta) {
        if (unlikesEvent == null || unlikesEvent.isEmpty()) return;

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            unlikesEvent.forEach((postId, events) -> {
                String stateKey = KeyUtils.getRedisStateKey(LIKE_STATE_CACHE, postId);
                byte[] stateKeyBytes = stateKey.getBytes(StandardCharsets.UTF_8);
                for (ReactionEvent event : events) {
                    connection.sRem(stateKeyBytes, event.getUserId().getBytes(StandardCharsets.UTF_8));
                }
                connection.expire(stateKeyBytes, LIKE_STATE_TTL.getSeconds());

                Long deletedCount = postsDelta.getOrDefault(postId, 0L);
                if (deletedCount > 0) {
                    String countKey = KeyUtils.getRedisCountKey(LIKE_COUNT_CACHE, postId);
                    byte[] countKeyBytes = countKey.getBytes(StandardCharsets.UTF_8);
                    connection.decrBy(countKeyBytes, deletedCount);
                    connection.expire(countKeyBytes, LIKE_COUNT_TTL.getSeconds());
                }
            });
            return null;
        });
    }
}
