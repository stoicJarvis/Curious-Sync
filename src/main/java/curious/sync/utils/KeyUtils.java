package curious.sync.utils;

import static curious.sync.constants.Strings.LIKED_BY_SUFFIX;
import static curious.sync.constants.Strings.LIKES_COUNT_SUFFIX;
import static curious.sync.constants.Strings.POST_KEY_PREFIX;

import lombok.experimental.UtilityClass;

@UtilityClass
public class KeyUtils {

    private static final String SEPARATOR = ":";

    public String getUserPostEventKey(Long userId, Long postId) {
        return userId + SEPARATOR + postId;
    }

    public String getRedisStateKey(String prefix, Long id) {
        return prefix + id;
    }

    public String getRedisCountKey(String prefix, Long id) {
        return prefix + id;
    }

    public static String generateUserPostKey(Long userId, Long postId) {
        return userId + SEPARATOR + postId;
    }

    /**
     * Idempotency set: {@code post:{postId}:liked_by}
     */
    public String likedByKey(Long postId) {
        return POST_KEY_PREFIX + postId + LIKED_BY_SUFFIX;
    }

    /**
     * Optimistic like counter: {@code post:{postId}:likes_count}
     */
    public String likesCountKey(Long postId) {
        return POST_KEY_PREFIX + postId + LIKES_COUNT_SUFFIX;
    }
}
