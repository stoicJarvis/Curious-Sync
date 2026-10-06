package curious.sync.repositories.scyllaDb.Likes;

import java.util.UUID;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import curious.sync.models.core.scyllaDb.Like.LikesCount;

@Repository
public interface LikesCountRepository extends CassandraRepository<LikesCount, Long> {

    // Bulk increment of likes
    @Query("UPDATE likesCount SET count = count + ?1 WHERE postId = ?0")
    void incrementCount(UUID postId, long incrementCountBy);

    // Bulk decrement of likes
    @Query("UPDATE likesCount SET count = count - ?1 WHERE postId = ?0")
    void decrementCount(UUID postId, long decrementCountBy);

    // Single Increment
    default void incrementByOne(UUID postId) {
        incrementCount(postId, 1L);
    }

    // Single Decrement
    default void decrementByOne(UUID postId) {
        decrementCount(postId, 1L);
    }

    default long getLikesCount(Long postId) {
        return findById(postId).map(LikesCount::getLikesCount).orElse(0L);
    }
}
