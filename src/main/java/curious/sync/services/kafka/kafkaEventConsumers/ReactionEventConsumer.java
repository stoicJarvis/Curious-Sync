package curious.sync.services.kafka.kafkaEventConsumers;

import static curious.sync.constants.Strings.LIKES_PROCESSOR_GROUP;
import static curious.sync.constants.Strings.LIKE_EVENT;
import static curious.sync.constants.Strings.UNLIKE_EVENT;

import java.util.List;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import curious.sync.models.Events.ReactionEvent;
import curious.sync.services.kafka.kafkaBatchProcessors.LikeBatchProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka batch consumer for like and unlike events.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReactionEventConsumer {

    private final LikeBatchProcessor likeBatchProcessor;

    /**
     * Batch consumes like events from Kafka topic and processes them.
     */
    @KafkaListener(topics = LIKE_EVENT, groupId = LIKES_PROCESSOR_GROUP, containerFactory = "kafkaListenerContainerFactory")
    public void consumeLikeBatch(@Payload List<ReactionEvent> likeEvents) {
        log.info("[like-consumer] Received batch of {} events", likeEvents.size());
        try {
            likeBatchProcessor.processReactionsBatch(likeEvents);
        } catch (Exception e) {
            log.error("[like-consumer] Failed to process batch of {} events: {}", likeEvents.size(), e.getMessage(), e);
        }
    }

    /**
     * Batch consumes unlike events from Kafka topic and processes them.
     */
    @KafkaListener(topics = UNLIKE_EVENT, groupId = LIKES_PROCESSOR_GROUP, containerFactory = "kafkaListenerContainerFactory")
    public void consumeUnlikeBatch(@Payload List<ReactionEvent> unlikEvents) {
        log.info("[unlike-consumer] Received batch of {} events", unlikEvents.size());
        try {
            likeBatchProcessor.processReactionsBatch(unlikEvents);
        } catch (Exception e) {
            log.error("[unlike-consumer] Failed to process batch of {} events: {}", unlikEvents.size(), e.getMessage(), e);
        }
    }
}
