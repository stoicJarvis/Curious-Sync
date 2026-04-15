package curious.sync.controllers.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import curious.sync.models.Post;
import curious.sync.models.User;
import curious.sync.repositories.UsersRepository;
import curious.sync.services.PostsService;

@RestController
@RequestMapping("/api/posts")
public class PostsController {
    
    @Autowired
    PostsService postsService;

    @Autowired
    private UsersRepository usersRepository;

    @GetMapping()
    public String postsGreet() {
        return new String("Greet from posts controller");
    }

    @PostMapping()
    public Post createNewPost(@RequestBody Post postToCreate) {

        User user = usersRepository.findById(postToCreate.getUser().getUser_id())
            .orElseThrow(() -> new RuntimeException("User not found"));

        postToCreate.setUser(user);

        return postsService.createNewPost(postToCreate);
    }
}
