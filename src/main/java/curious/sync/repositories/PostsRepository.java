package curious.sync.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import curious.sync.models.Post;

@Repository
public interface PostsRepository extends JpaRepository<Post, String> {
    
}
