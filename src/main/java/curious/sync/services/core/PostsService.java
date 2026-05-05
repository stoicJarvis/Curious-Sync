package curious.sync.services.core;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import curious.sync.configurations.RequestCoalescer.RequestCoalescer;
import curious.sync.models.core.Post;
import curious.sync.repositories.PostsRepository;
import curious.sync.services.redis.likesCache.LikeStateCache;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PostsService {

    private final PostsRepository postsRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final LikeStateCache likeStateCache;

    private final RequestCoalescer<Post> getPostCoalescer = new RequestCoalescer<>("getPostById");
    
    private static final String CACHE_KEY_PREFIX = "post:";

    public PostsService(PostsRepository postsRepository,
                        RedisTemplate<String, Object> redisTemplate,
                        LikeStateCache likeStateCache) {
        this.postsRepository = postsRepository;
        this.redisTemplate = redisTemplate;
        this.likeStateCache = likeStateCache;
    }

    public Post createNewPost(Post postToCreate) {
        return postsRepository.save(postToCreate);
    }

    public Post getPostById(String postId) {
        return getPostCoalescer.coalesce(postId, () -> {
            String postKey = CACHE_KEY_PREFIX + postId;

            Post cachedPost = (Post) redisTemplate.opsForValue().get(postKey);
            if (cachedPost != null) {
                log.info("CACHE HIT FOR POSTID {}", postId);
                return cachedPost;
            }

            Post post = postsRepository.findById(postId)
                    .orElseThrow(() -> new RuntimeException("Post not found with id: " + postId));

            log.info("DB READ FOR POSTID {}", postId);
            redisTemplate.opsForValue().set(postKey, post, Duration.ofSeconds(120));
            return post;
        });
    }

    /**
     * Returns like count for the given postId
     */
    public Long getLikesCount(String postId) {
        return likeStateCache.getCount(postId);
    }

    public Post getPost(String postId) {
        return postsRepository.findById(postId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found with id: " + postId));
    }
}
