package curious.sync.controllers.core;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import curious.sync.models.Post;
import curious.sync.models.User;
import curious.sync.repositories.PostsRepository;
import curious.sync.repositories.UsersRepository;
import curious.sync.services.LikesService;

@RestController
@RequestMapping("/api/likes")
public class LikesController {

    @Autowired
    LikesService likesService;

    @Autowired
    private UsersRepository usersRepository;

    @Autowired
    private PostsRepository postsRepository;

    @GetMapping()
    public String likesGreet() {
        return "Greet from likes controller";
    }

    @PostMapping("/react")
    public Map<String, Object> react(@RequestBody Map<String, String> requestBody) {
        String userId = requestBody.get("user_id");
        String postId = requestBody.get("post_id");

        User user = usersRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        Post post = postsRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));

        return likesService.react(user, post);
    }
}
