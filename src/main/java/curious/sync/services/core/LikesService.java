package curious.sync.services.core;

import static curious.sync.constants.Strings.LIKE_EVENT;
import static curious.sync.constants.Strings.UNLIKE_EVENT;

import java.util.Map;

import org.springframework.stereotype.Service;

import curious.sync.models.Events.ReactionEvent;
import curious.sync.models.core.Post;
import curious.sync.models.core.User;
import curious.sync.repositories.Likes.LikesRepository;
import curious.sync.services.kafka.kafkaEventProducers.ReactionEventProducer;
import curious.sync.services.redis.likesCache.LikeStateCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class LikesService {

    private final LikesRepository likesRepository;
    private final ReactionEventProducer reactionEventProducer;
    private final LikeStateCache likeStateCache;

    /**
     * Adds like/unlike event in the Kafka topic and returns the event type immediately
     */
    public Map<String, Object> react(User user, Post post) {
        String userId = user.getUser_id();
        String postId = post.getPost_id();

        boolean hasLiked = hasUserLikedThePost(userId, postId, user, post);

        ReactionEvent event = ReactionEvent.builder()
                .postId(postId)
                .userId(userId)
                .build();

        if (hasLiked) {
            event.setEventType(UNLIKE_EVENT);

            reactionEventProducer.sendUnlikeEvent(event);
            log.debug("Unlike event queued — user={} post={}", userId, postId);

            return Map.of("action", UNLIKE_EVENT, "post_id", postId);
        } else {
            event.setEventType(LIKE_EVENT);

            reactionEventProducer.sendLikeEvent(event);
            log.debug("Like event queued — user={} post={}", userId, postId);

            return Map.of("action", LIKE_EVENT, "post_id", postId);
        }
    }

    /**
     * Check if user has already liked the post or not (First looks into redis cache, if not available then into db as a fallback)
     */
    private boolean hasUserLikedThePost(String userId, String postId, User user, Post post) {
        if (likeStateCache.hasLiked(postId, userId)) {
            log.debug("Like state resolved from Redis — user={} post={}", userId, postId);
            return true;
        }

        boolean hasLiked = likesRepository.findByUserAndPost(user, post).isPresent();
        if (hasLiked) {
            // udpate redis state
            likeStateCache.markLiked(postId, userId);
            log.debug("Like state seeded into Redis from DB — user={} post={}", userId, postId);
        }
        return hasLiked;
    }
}
