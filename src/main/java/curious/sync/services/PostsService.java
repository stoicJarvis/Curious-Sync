package curious.sync.services;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import curious.sync.models.Post;
import curious.sync.repositories.PostsRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PostsService {
    
    private final PostsRepository postsRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String CACHE_KEY_PREFIX = "post:";

    public PostsService(PostsRepository postsRepository, RedisTemplate<String, Object> redisTemplate) {
        this.postsRepository = postsRepository;
        this.redisTemplate = redisTemplate;
    }

    public Post createNewPost(Post postToCreate) {
        return postsRepository.save(postToCreate);
    }

    public Post getPostById(String postId) {

        String postKey = CACHE_KEY_PREFIX + postId;

        Post cachedPost = (Post) redisTemplate.opsForValue().get(postKey);
        if(cachedPost != null) {
            log.info("CACHE HIT FOR POSTID {}", postId);
            return cachedPost;
        }

        Post post = postsRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found with id: " + postId));

        log.info("DB READ FOR POSTID {}", postId);

        redisTemplate.opsForValue().set(postKey, post, Duration.ofSeconds(120));

        return post;
    }

    public Long getLikesCount(String postId) {
        return postsRepository.findById(postId).get().getTotal_likes();
    }
}
