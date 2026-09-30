package curious.sync.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class KeyUtils {

    private static final String SEPARATOR = ":";

    public String getUserPostEventKey(String userId, Long postId) {
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
}
