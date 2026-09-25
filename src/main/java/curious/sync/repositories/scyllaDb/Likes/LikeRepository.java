package curious.sync.repositories.scyllaDb.Likes;

import java.util.List;
import java.util.UUID;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import curious.sync.models.core.scyllaDb.Like.Like;
import curious.sync.models.core.scyllaDb.Like.LikeKey;

@Repository
public interface LikeRepository extends CassandraRepository<Like, LikeKey> {

    @Query("SELECT * FROM post_likes WHERE post_id = ?0")
    List<Like> getUserLikesForPost(UUID postId);

    @Query("SELECT count(*) > 0 FROM post_likes WHERE post_id = ?0 AND user_id = ?1")
    boolean hasUserLiked(UUID postId, UUID userId);
}