package curious.sync.repositories.Likes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import curious.sync.models.Events.ReactionEvent;
import curious.sync.utils.KeyUtils;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class LikesRepositoryImpl implements ILikesRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Filters out events that already exist in the database.
     * Performs a single DB trip to check existence of all (userId, postId) pairs.
     *
     * @param events Map of postId to list of ReactionEvents
     * @return Filtered map containing only events that don't exist in DB
     */
    @Override
    public Map<String, List<ReactionEvent>> filterAlreadyLikedEvents(Map<String, List<ReactionEvent>> events) {
        if (events == null || events.isEmpty()) {
            return events;
        }

        List<ReactionEvent> allEvents = events.values().stream()
                .flatMap(List::stream)
                .toList();

        String[] userIds = allEvents.stream().map(ReactionEvent::getUserId).toArray(String[]::new);
        String[] postIds = allEvents.stream().map(ReactionEvent::getPostId).toArray(String[]::new);

        // Single query to identify existing (user, post) pairs using UNNEST for
        // efficiency
        String sql = """
                SELECT l.user_id, l.post_id
                FROM likes l
                JOIN (
                    SELECT UNNEST(?) as u_id, UNNEST(?) as p_id
                ) as input ON l.user_id = input.u_id AND l.post_id = input.p_id
                """;

        Set<String> existingKeys = jdbcTemplate.execute((Connection connection) -> {
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setArray(1, connection.createArrayOf("text", userIds));
                preparedStatement.setArray(2, connection.createArrayOf("text", postIds));
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    Set<String> existingKeysSet = new HashSet<>();
                    while (resultSet.next()) {
                        existingKeysSet.add(KeyUtils.getUserPostEventKey(resultSet.getString("user_id"),
                                resultSet.getString("post_id")));
                    }
                    return existingKeysSet;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Error filtering existing likes", e);
            }
        });

        if (existingKeys == null || existingKeys.isEmpty()) {
            return events;
        }

        // Return a filtered map with only non-existent likes
        Map<String, List<ReactionEvent>> filteredEvents = new HashMap<>();
        events.forEach((postId, postEvents) -> {
            List<ReactionEvent> filtered = postEvents.stream()
                    .filter(e -> !existingKeys.contains(KeyUtils.getUserPostEventKey(e.getUserId(), e.getPostId())))
                    .toList();
            if (!filtered.isEmpty()) {
                filteredEvents.put(postId, filtered);
            }
        });

        return filteredEvents;
    }

    /**
     * Inserts likes and increments post like counts in a single atomic DB trip.
     * Uses PostgreSQL CTE and UNNEST for maximum performance on large batches.
     *
     * @param events Map of postId to list of ReactionEvents to insert
     */
    @Override
    public void insertBatchOfLikeAndLikesCount(Map<String, List<ReactionEvent>> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        List<ReactionEvent> allEvents = events.values().stream()
                .flatMap(List::stream)
                .toList();

        String[] userIds = allEvents.stream().map(ReactionEvent::getUserId).toArray(String[]::new);
        String[] postIds = allEvents.stream().map(ReactionEvent::getPostId).toArray(String[]::new);

        // Optimized CTE: 
        // 1. Insert unique likes,
        // 2. Count insertions per post, 
        // 3. Update post counts
        String sql = """
                WITH input_data AS (
                    SELECT UNNEST(?) as u_id, UNNEST(?) as p_id
                ),
                inserted_likes AS (
                    INSERT INTO likes (user_id, post_id)
                    SELECT u_id, p_id FROM input_data
                    ON CONFLICT (user_id, post_id) DO NOTHING
                    RETURNING post_id
                )
                UPDATE posts
                SET total_likes = total_likes + counts.like_count
                FROM (
                    SELECT post_id, count(*) as like_count
                    FROM inserted_likes
                    GROUP BY post_id
                ) AS counts
                WHERE posts.post_id = counts.post_id
                """;

        jdbcTemplate.execute(sql, (PreparedStatement preparedStatement) -> {
            Connection connection = preparedStatement.getConnection();
            preparedStatement.setArray(1, connection.createArrayOf("text", userIds));
            preparedStatement.setArray(2, connection.createArrayOf("text", postIds));
            return preparedStatement.executeUpdate();
        });
    }
}
