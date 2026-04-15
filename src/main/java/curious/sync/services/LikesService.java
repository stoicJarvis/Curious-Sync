package curious.sync.services;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import curious.sync.models.Like;
import curious.sync.models.Post;
import curious.sync.models.User;
import curious.sync.repositories.LikesRepository;
import curious.sync.repositories.PostsRepository;

@Service
public class LikesService {

    @Autowired
    private LikesRepository likesRepository;

    @Autowired
    private PostsRepository postsRepository;

    public Map<String, Object> react(User user, Post post) {
        Optional<Like> existingLike = likesRepository.findByUserAndPost(user, post);

        if (existingLike.isPresent()) {
            likesRepository.delete(existingLike.get());
            post.setTotal_likes(post.getTotal_likes() - 1);
            postsRepository.save(post);
            return Map.of("action", "unliked", "post_id", post.getPost_id(), "total_likes", post.getTotal_likes());
        } else {
            Like like = Like.builder()
                            .user(user)
                            .post(post)
                            .build();
            likesRepository.save(like);

            post.setTotal_likes(post.getTotal_likes() + 1);
            postsRepository.save(post);

            return Map.of("action", "liked", "post_id", post.getPost_id(), "total_likes", post.getTotal_likes());
        }
    }
}
