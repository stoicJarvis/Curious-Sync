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
}
