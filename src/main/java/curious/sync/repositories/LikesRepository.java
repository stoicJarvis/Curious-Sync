package curious.sync.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import curious.sync.models.Like;
import curious.sync.models.Post;
import curious.sync.models.User;

@Repository
public interface LikesRepository extends JpaRepository<Like, String> {
    Optional<Like> findByUserAndPost(User user, Post post);
}
