package curious.sync.controllers.core;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import curious.sync.models.Post;
import curious.sync.models.User;
import curious.sync.repositories.PostsRepository;
import curious.sync.repositories.UsersRepository;
import curious.sync.services.LikesService;
import curious.sync.services.PostsService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/likes")
public class LikesController {

    @Autowired
    LikesService likesService;

    @Autowired
    PostsService postsService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private PostsRepository postsRepository;

    @GetMapping()
    public String likesGreet() {
        log.debug("GET /api/likes - greet endpoint hit");
        return "Greet from likes controller";
    }

    @GetMapping("/getLikes")
    public Long getLikesCount(@RequestParam String postId) {
        log.info("GET /api/likes/getLikes - fetching likes count for postId: {}", postId);
        Long count = postsService.getLikesCount(postId);
        return count;
    }

    @PostMapping("/react")
    public Map<String, Object> react(@RequestBody Map<String, String> requestBody) {
        String userId = requestBody.get("user_id");
        String postId = requestBody.get("post_id");
        log.info("POST /api/likes/react - user: {} reacting on post: {}", userId, postId);

        User user = usersRepository.findById(userId)
            .orElseThrow(() -> {
                log.error("User not found with id: {}", userId);
                return new RuntimeException("User not found");
            });
        Post post = postsRepository.findById(postId)
            .orElseThrow(() -> {
                log.error("Post not found with id: {}", postId);
                return new RuntimeException("Post not found");
            });

        Map<String, Object> result = likesService.react(user, post);
        return result;
    }
}
