package curious.sync.services.kafka.kafkaEventProducers;

import static curious.sync.constants.Strings.LIKE_EVENT;
import static curious.sync.constants.Strings.UNLIKE_EVENT;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import curious.sync.models.Events.ReactionEvent;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ReactionEventProducer {

    @Autowired
    private KafkaTemplate<String, ReactionEvent> kafkaTemplate;

    /**
     * Pushes the like event into kafka topic
     */
    public void sendLikeEvent(ReactionEvent reactionEvent) {
        kafkaTemplate.send(LIKE_EVENT, reactionEvent.getPostId(), reactionEvent);
        log.debug("Like event queued — user={} post={}", reactionEvent.getUserId(), reactionEvent.getPostId());
    }

    /**
     * Pushes the unlike event into kafka topic
     */
    public void sendUnlikeEvent(ReactionEvent reactionEvent) {
        kafkaTemplate.send(UNLIKE_EVENT, reactionEvent.getPostId(), reactionEvent);
    }
}
