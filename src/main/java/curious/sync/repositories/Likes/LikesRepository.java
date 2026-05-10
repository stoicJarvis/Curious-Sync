package curious.sync.repositories.Likes;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import curious.sync.models.core.Like;
import curious.sync.models.core.Post;
import curious.sync.models.core.User;

@Repository
public interface LikesRepository extends JpaRepository<Like, String>, ILikesRepository {

    /**
     * Finds a like by user and post.
     */
    Optional<Like> findByUserAndPost(User user, Post post);

    /**
     * Returns the subset of `userIds` that have already liked `postId`.
     */
    @Query("SELECT like.user.user_id FROM Like like WHERE like.post.post_id = :postId AND like.user.user_id IN :userIds")
    Set<String> findExistingLikerIds(@Param("postId") String postId,
            @Param("userIds") Collection<String> userIds);

    /**
     * Atomic single-row delete for unlike events.
     * Returns the number of rows deleted (0 = like didn't exist, 1 = deleted).
     * Avoids loading the entity just to delete it.
     */
    @Modifying
    @Query("DELETE FROM Like like WHERE like.user.user_id = :userId AND like.post.post_id = :postId")
    int deleteByUserIdAndPostId(@Param("userId") String userId, @Param("postId") String postId);
}
