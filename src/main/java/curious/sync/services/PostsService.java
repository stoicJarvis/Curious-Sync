package curious.sync.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import curious.sync.models.Post;
import curious.sync.repositories.PostsRepository;

@Service
public class PostsService {
    
    @Autowired
    private PostsRepository postsRepository;

    public Post createNewPost(Post postToCreate) {
        return postsRepository.save(postToCreate);
    }

    public Post getPostById(String postId) {
        return postsRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found with id: " + postId));
    }

    public Long getLikesCount(String postId) {
        return postsRepository.findById(postId).get().getTotal_likes();
    }
}
