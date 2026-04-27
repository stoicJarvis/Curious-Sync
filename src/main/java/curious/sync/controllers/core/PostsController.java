package curious.sync.controllers.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import curious.sync.models.Post;
import curious.sync.models.User;
import curious.sync.services.PostsService;
import curious.sync.services.UsersService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/posts")
public class PostsController {

    @Autowired
    PostsService postsService;

    @Autowired
    private UsersService usersService;

    @GetMapping()
    public String postsGreet() {
        log.debug("GET /api/posts - greet endpoint hit");
        return new String("Greet from posts controller");
    }

    @PostMapping()
    public Post createNewPost(@RequestBody Post postToCreate) {
        log.info("POST /api/posts - creating post for user_id: {}", postToCreate.getUser().getUser_id());

        User user = usersService.getUser(postToCreate.getUser().getUser_id());

        postToCreate.setUser(user);

        Post created = postsService.createNewPost(postToCreate);
        log.info("Post created successfully with id: {} by user: {}", created.getPost_id(), user.getUser_id());
        return created;
    }

    @GetMapping("/{postId}")
    public Post getPost(@PathVariable String postId) {
        log.info("GET /api/posts/{} - fetching post", postId);
        return postsService.getPostById(postId);
    }
}
