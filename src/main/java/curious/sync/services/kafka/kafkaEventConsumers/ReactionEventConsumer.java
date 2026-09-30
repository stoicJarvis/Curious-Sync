package curious.sync.services.kafka.kafkaEventConsumers;

import static curious.sync.constants.Strings.LIKES_PROCESSOR_GROUP;
import static curious.sync.constants.Strings.LIKE_EVENT;

import java.util.List;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import curious.sync.models.Events.ReactionEvent;
import curious.sync.services.kafka.kafkaBatchProcessors.ReactionsBatchProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka batch consumer for like and unlike events.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReactionEventConsumer {
    private final ReactionsBatchProcessor likesBatchProcessor;

    /**
     * Batch consumes like events from Kafka topic and processes them.
     */
    @KafkaListener(topics = LIKE_EVENT, groupId = LIKES_PROCESSOR_GROUP, containerFactory = "kafkaListenerContainerFactory")
    public void consumeLikesBatch(@Payload List<ReactionEvent> likeEvents) {
        log.info("[like-consumer] Received batch of {} events", likeEvents.size());
        likesBatchProcessor.processBatchOfLikes(likeEvents);
    }
}
