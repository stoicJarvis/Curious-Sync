package curious.sync.utils;

import java.time.Duration;

import lombok.experimental.UtilityClass;

/**
 * Extracts the embedded timestamp from a 64-bit Snowflake ID and derives a
 * dynamic Redis TTL from post age. Newer posts stay hot in cache longer.
 *
 * Layout assumed: [41-bit timestamp][10-bit worker][12-bit sequence]
 * Timestamp is milliseconds since {@link #CUSTOM_EPOCH_MS}.
 */
@UtilityClass
public class SnowflakeUtils {

    /** Custom epoch: 2024-01-01T00:00:00Z */
    public static final long CUSTOM_EPOCH_MS = 1704067200000L;

    private static final int TIMESTAMP_SHIFT = 22;

    private static final long ONE_DAY_MS = Duration.ofDays(1).toMillis();
    private static final long SEVEN_DAYS_MS = Duration.ofDays(7).toMillis();
    private static final long THIRTY_DAYS_MS = Duration.ofDays(30).toMillis();

    private static final Duration TTL_UNDER_24H = Duration.ofDays(7);
    private static final Duration TTL_UNDER_7D = Duration.ofHours(24);
    private static final Duration TTL_UNDER_30D = Duration.ofHours(1);
    private static final Duration TTL_OLDER = Duration.ofMinutes(5);

    /**
     * Recovers the wall-clock timestamp (epoch millis) encoded in a Snowflake ID.
     */
    public long extractTimestamp(long snowflakeId) {
        return (snowflakeId >>> TIMESTAMP_SHIFT) + CUSTOM_EPOCH_MS;
    }

    /**
     * TTL for caching a post's like count, based solely on the Snowflake ID
     * No database lookup is required.
     */
    public Duration calculateDynamicTtl(long postId) {
        long ageMs = System.currentTimeMillis() - extractTimestamp(postId);

        if (ageMs < ONE_DAY_MS) return TTL_UNDER_24H;
        if (ageMs < SEVEN_DAYS_MS) return TTL_UNDER_7D;
        if (ageMs < THIRTY_DAYS_MS) return TTL_UNDER_30D;

        return TTL_OLDER;
    }
}
