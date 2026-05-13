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
            } catch (SQLException error) {
                throw new RuntimeException("Error filtering existing likes", error);
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
     * Inserts likes and increments post like counts in a single DB trip.
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

        String sql = """
                WITH input_data AS (
                    SELECT UNNEST(?) as u_id, UNNEST(?) as p_id
                ),
                inserted_likes AS (
                    INSERT INTO likes (user_id, post_id)
                    SELECT u_id, p_id FROM input_data
                    ON CONFLICT (user_id, post_id) DO NOTHING
                    RETURNING post_id
                ),
                counts AS (
                    SELECT post_id, count(*) as like_count
                    FROM inserted_likes
                    GROUP BY post_id
                ),
                lock_rows AS (
                    SELECT post_id FROM posts
                    WHERE post_id IN (SELECT post_id FROM counts)
                    ORDER BY post_id
                    FOR UPDATE
                )
                UPDATE posts
                SET total_likes = total_likes + counts.like_count
                FROM counts
                WHERE posts.post_id = counts.post_id
                AND EXISTS (SELECT 1 FROM lock_rows WHERE lock_rows.post_id = posts.post_id)
                """;

        jdbcTemplate.execute(sql, (PreparedStatement preparedStatement) -> {
            Connection connection = preparedStatement.getConnection();
            preparedStatement.setArray(1, connection.createArrayOf("text", userIds));
            preparedStatement.setArray(2, connection.createArrayOf("text", postIds));
            return preparedStatement.executeUpdate();
        });
    }

    @Override
    public Map<String, List<ReactionEvent>> discardOrphanedUnlikes(Map<String, List<ReactionEvent>> events) {
        if (events == null || events.isEmpty()) {
            return events;
        }

        List<ReactionEvent> allEvents = events.values().stream()
                .flatMap(List::stream)
                .toList();

        String[] userIds = allEvents.stream().map(ReactionEvent::getUserId).toArray(String[]::new);
        String[] postIds = allEvents.stream().map(ReactionEvent::getPostId).toArray(String[]::new);

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
            } catch (SQLException error) {
                throw new RuntimeException("Error filtering existing likes", error);
            }
        });

        if (existingKeys == null || existingKeys.isEmpty()) {
            return new HashMap<>();
        }

        // Return a map with only those unlikes that have a corresponding like in the DB
        Map<String, List<ReactionEvent>> filteredEvents = new HashMap<>();
        events.forEach((postId, postEvents) -> {
            List<ReactionEvent> filtered = postEvents.stream()
                    .filter(e -> existingKeys.contains(KeyUtils.getUserPostEventKey(e.getUserId(), e.getPostId())))
                    .toList();
            if (!filtered.isEmpty()) {
                filteredEvents.put(postId, filtered);
            }
        });

        return filteredEvents;
    }

    @Override
    public void deleteBatchOfUnlikeAndLikesCount(Map<String, List<ReactionEvent>> unlikesEvent) {
        if (unlikesEvent == null || unlikesEvent.isEmpty()) {
            return;
        }
        
        List<ReactionEvent> allEvents = unlikesEvent.values().stream()
                .flatMap(List::stream)
                .toList();

        String[] userIds = allEvents.stream().map(ReactionEvent::getUserId).toArray(String[]::new);
        String[] postIds = allEvents.stream().map(ReactionEvent::getPostId).toArray(String[]::new);

        String sql = """
                WITH input_data AS (
                    SELECT UNNEST(?) as u_id, UNNEST(?) as p_id
                ),
                deleted_likes AS (
                    DELETE FROM likes
                    WHERE EXISTS (
                        SELECT 1
                        FROM input_data
                        WHERE likes.user_id = input_data.u_id
                        AND likes.post_id = input_data.p_id
                    )
                    RETURNING post_id
                ),
                counts AS (
                    SELECT post_id, count(*) as unlike_count
                    FROM deleted_likes
                    GROUP BY post_id
                ),
                lock_rows AS (
                    SELECT post_id FROM posts
                    WHERE post_id IN (SELECT post_id FROM counts)
                    ORDER BY post_id
                    FOR UPDATE
                )
                UPDATE posts
                SET total_likes = total_likes - counts.unlike_count
                FROM counts
                WHERE posts.post_id = counts.post_id
                AND EXISTS (SELECT 1 FROM lock_rows WHERE lock_rows.post_id = posts.post_id)
                """;

        jdbcTemplate.execute(sql, (PreparedStatement preparedStatement) -> {
            Connection connection = preparedStatement.getConnection();
            preparedStatement.setArray(1, connection.createArrayOf("text", userIds));
            preparedStatement.setArray(2, connection.createArrayOf("text", postIds));
            return preparedStatement.executeUpdate();
        });
    }
}
