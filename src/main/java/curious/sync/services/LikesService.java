package curious.sync.services;

import static curious.sync.constants.Strings.LIKE_EVENT;
import static curious.sync.constants.Strings.UNLIKE_EVENT;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import curious.sync.models.Like;
import curious.sync.models.Post;
import curious.sync.models.User;
import curious.sync.models.Events.ReactionEvent;
import curious.sync.repositories.LikesRepository;
import curious.sync.services.kafka.kafkaEventProducers.ReactionEventProducer;

@Service
public class LikesService {

    @Autowired
    private LikesRepository likesRepository;

    @Autowired
    private ReactionEventProducer reactionEventProducer;

    public Map<String, Object> react(User user, Post post) {
        Optional<Like> existingLike = likesRepository.findByUserAndPost(user, post);

        ReactionEvent reactionEvent = ReactionEvent.builder()
                .postId(post.getPost_id())
                .userId(user.getUser_id())
                .build();

        if (existingLike.isPresent()) {
            reactionEvent.setEventType(UNLIKE_EVENT);
            reactionEventProducer.sendUnlikeEvent(reactionEvent);

            return Map.of("action", UNLIKE_EVENT, "post_id", post.getPost_id(), "total_likes",
                    post.getTotal_likes());
        } else {
            reactionEvent.setEventType(LIKE_EVENT);
            reactionEventProducer.sendLikeEvent(reactionEvent);

            return Map.of("action", LIKE_EVENT, "post_id", post.getPost_id(), "total_likes",
                    post.getTotal_likes());
        }
    }
}
