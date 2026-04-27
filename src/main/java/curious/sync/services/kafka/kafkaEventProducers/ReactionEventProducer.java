package curious.sync.services.kafka.kafkaEventProducers;

import static curious.sync.constants.Strings.LIKE_EVENT;
import static curious.sync.constants.Strings.UNLIKE_EVENT;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import curious.sync.models.Events.ReactionEvent;

@Service
public class ReactionEventProducer {

    @Autowired
    private KafkaTemplate<String, ReactionEvent> kafkaTemplate;

    public void sendLikeEvent(ReactionEvent reactionEvent) {
        kafkaTemplate.send(LIKE_EVENT, reactionEvent.getPostId(), reactionEvent);
    }

    public void sendUnlikeEvent(ReactionEvent reactionEvent) {
        kafkaTemplate.send(UNLIKE_EVENT, reactionEvent.getPostId(), reactionEvent);
    }
}
