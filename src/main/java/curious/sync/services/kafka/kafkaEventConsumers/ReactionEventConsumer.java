package curious.sync.services.kafka.kafkaEventConsumers;

import static curious.sync.constants.Strings.LIKES_PROCESSOR_GROUP;
import static curious.sync.constants.Strings.LIKE_EVENT;
import static curious.sync.constants.Strings.UNLIKE_EVENT;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import curious.sync.models.Like;
import curious.sync.models.Post;
import curious.sync.models.User;
import curious.sync.models.Events.ReactionEvent;
import curious.sync.repositories.LikesRepository;
import curious.sync.repositories.PostsRepository;
import curious.sync.services.PostsService;
import curious.sync.services.UsersService;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ReactionEventConsumer {

    @Autowired
    private LikesRepository likesRepository;

    @Autowired
    private PostsRepository postsRepository;

    @Autowired
    private UsersService usersService;

    @Autowired
    private PostsService postsService;

    @KafkaListener(topics = LIKE_EVENT, groupId = LIKES_PROCESSOR_GROUP, containerFactory = "kafkaListenerContainerFactory")
    public void reactionConsumer(@Payload ReactionEvent reactionEvent) {
        if (reactionEvent.getEventType().contentEquals(LIKE_EVENT)) {
            handleLikeEvent(reactionEvent);
        } else {
            log.warn("Unknown event type received: {}", reactionEvent.getEventType());
        }
    }

    @KafkaListener(topics = UNLIKE_EVENT, groupId = LIKES_PROCESSOR_GROUP, containerFactory = "kafkaListenerContainerFactory")
    public void unlikeEventConsumer(@Payload ReactionEvent reactionEvent) {
        if (reactionEvent.getEventType().contentEquals(UNLIKE_EVENT)) {
            handleUnlikeEvent(reactionEvent);
        } else {
            log.warn("Unknown event type received: {}", reactionEvent.getEventType());
        }
    }

    private void handleLikeEvent(ReactionEvent reactionEvent) {
        try {
            User user = usersService.getUser(reactionEvent.getUserId());
            Post post = postsService.getPost(reactionEvent.getPostId());

            // Check if like already exists
            if (likesRepository.findByUserAndPost(user, post).isEmpty()) {
                Like like = Like.builder()
                        .user(user)
                        .post(post)
                        .build();
                likesRepository.save(like);

                // Increment total likes
                post.setTotal_likes(post.getTotal_likes() + 1);
                postsRepository.save(post);

                log.info("Like added for post: {} by user: {}", post.getPost_id(), user.getUser_id());
            } else {
                log.info("Like already exists for post: {} by user: {}", post.getPost_id(), user.getUser_id());
            }
        } catch (Exception e) {
            log.error("Error processing like event: {}", e.getMessage(), e);
        }
    }

    private void handleUnlikeEvent(ReactionEvent reactionEvent) {
        try {
            User user = usersService.getUser(reactionEvent.getUserId());
            Post post = postsService.getPost(reactionEvent.getPostId());

            // Find and remove the like
            likesRepository.findByUserAndPost(user, post).ifPresent(like -> {
                likesRepository.delete(like);

                // Decrement total likes
                post.setTotal_likes(Math.max(0, post.getTotal_likes() - 1));
                postsRepository.save(post);

                log.info("Like removed for post: {} by user: {}", post.getPost_id(), user.getUser_id());
            });
        } catch (Exception e) {
            log.error("Error processing unlike event: {}", e.getMessage(), e);
        }
    }
}
