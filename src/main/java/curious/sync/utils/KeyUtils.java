package curious.sync.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class KeyUtils {

    private static final String SEPARATOR = ":";

    public String getUserPostEventKey(String userId, String postId) {
        return userId + SEPARATOR + postId;
    }

    public String getRedisStateKey(String prefix, String id) {
        return prefix + id;
    }

    public String getRedisCountKey(String prefix, String id) {
        return prefix + id;
    }

    public static String generateUserPostKey(String userId, String postId) {
        return userId + SEPARATOR + postId;
    }
}
