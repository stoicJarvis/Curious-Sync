package curious.sync.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import curious.sync.models.core.Post;

@Repository
public interface PostsRepository extends JpaRepository<Post, String> {

    /**
     * Atomically increments total_likes by `delta` for the given post.
     * Replaces the old read-modify-write pattern that caused race conditions.
     */
    @Modifying
    @Query(value = "UPDATE posts SET total_likes = total_likes + :delta WHERE post_id = :postId",
           nativeQuery = true)
    void incrementLikesBy(@Param("postId") String postId, @Param("delta") long delta);

    /**
     * Atomically decrements total_likes by `delta`, flooring at 0.
     * GREATEST prevents negative counts from stale events or race conditions.
     */
    @Modifying
    @Query(value = "UPDATE posts SET total_likes = GREATEST(total_likes - :delta, 0) WHERE post_id = :postId",
           nativeQuery = true)
    void decrementLikesBy(@Param("postId") String postId, @Param("delta") long delta);
}
