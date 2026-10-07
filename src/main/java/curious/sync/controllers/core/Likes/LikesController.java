package curious.sync.controllers.core.Likes;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import curious.sync.services.core.Likes.LikesService;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@RestController
@RequestMapping("/api/like")
public class LikesController {

    @Autowired
    LikesService likesService;

    @PostMapping("/")
    public Map<String, Object> like(@RequestBody Map<String, Long> requestBody) {
        Long userId = requestBody.get("userId");
        Long postId = requestBody.get("postId");

        log.info("POST /api/likes/react - user: {} reacting on post: {}", userId, postId);

        Map<String, Object> result = likesService.likePost(userId, postId);

        return result;
    }

    @GetMapping("/getLikes")
    public long getLikesCount(@RequestParam Long postId) {
        return likesService.getLikesCount(postId);
    }
    
}
