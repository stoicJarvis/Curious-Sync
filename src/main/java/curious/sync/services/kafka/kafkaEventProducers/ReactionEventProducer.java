package curious.sync.services.kafka.kafkaEventProducers;

import static curious.sync.constants.Strings.LIKE_EVENT;

import java.util.concurrent.ExecutionException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import curious.sync.models.Events.ReactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReactionEventProducer {

    private final KafkaTemplate<Long, ReactionEvent> kafkaTemplate;

    /**
     * Publishes a like event, partitioned by postId. Blocks until the broker acks
     * so the caller can roll back Redis on failure.
     */
    public void sendLikeEvent(ReactionEvent reactionEvent) {
        try {
            kafkaTemplate.send(LIKE_EVENT, reactionEvent.getPostId(), reactionEvent).get();
            log.debug("Like event queued — user={} post={}", reactionEvent.getUserId(), reactionEvent.getPostId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing like event", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to publish like event", e.getCause());
        }
    }
}
