package curious.sync.controllers.core.Likes;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import curious.sync.services.core.LikesService;
import curious.sync.services.core.PostsService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/like")
public class LikeController {

    @Autowired
    LikesService likesService;

    @Autowired
    PostsService postsService;

    @PostMapping("/")
    public Map<String, Object> react(@RequestBody Map<String, String> requestBody) {
        UUID userId = UUID.fromString(requestBody.get("user_id"));
        UUID postId = UUID.fromString(requestBody.get("post_id"));

        log.info("POST /api/likes/react - user: {} reacting on post: {}", userId, postId);

        Map<String, Object> result = likesService.likePost(userId, postId);

        return result;
    }
}
