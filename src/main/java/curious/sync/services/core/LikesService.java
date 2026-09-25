package curious.sync.services.core;

import static curious.sync.constants.Strings.LIKE_EVENT;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import curious.sync.constants.ReactionAction;
import curious.sync.models.Events.ReactionEvent;
import curious.sync.services.kafka.kafkaEventProducers.ReactionEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class LikesService {

    private final ReactionEventProducer reactionEventProducer;

     /**
     * Adds like event in the Kafka topic and returns the event type immediately
     */
    public Map<String, Object> likePost(UUID userId, UUID postId) {

        ReactionEvent event = ReactionEvent.builder()
                .postId(postId)
                .userId(userId)
                .reactionAction(ReactionAction.LIKE)
                .build();

        reactionEventProducer.sendLikeEvent(event);

        return Map.of("action", LIKE_EVENT, "post_id", postId);
    }
}
