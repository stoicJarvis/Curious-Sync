package curious.sync.configurations.Kafka;

import static curious.sync.constants.Strings.LIKES_PROCESSOR_GROUP;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import curious.sync.models.Events.ReactionEvent;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    ConsumerFactory<String, ReactionEvent> consumerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, LIKES_PROCESSOR_GROUP);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // ── Batch fetch tuning ────────────────────────────────────────────────────
        // Pull up to 1000 records per poll() call (default: 500).
        // Larger polls = bigger batches for LikeBatchProcessor = fewer DB round-trips.
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000);

        // Wait until at least 64KB of data is available before returning from fetch.
        // Pairs with fetch.max.wait.ms to amortise overhead on high-throughput topics.
        config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 65536);

        // Max time to wait for fetch.min.bytes to be satisfied (500ms).
        // Matches producer linger.ms — ensures we're never waiting longer than the
        // producer's batching window.
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        // Disable auto-commit — we commit manually (AckMode.BATCH) only after
        // LikeBatchProcessor successfully processes the entire batch.
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        JsonDeserializer<ReactionEvent> deserializer = new JsonDeserializer<>(ReactionEvent.class);
        deserializer.addTrustedPackages("curious.sync.models.Events.*");
        deserializer.setUseTypeMapperForKey(false);

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, ReactionEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ReactionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // Enable batch listener — consumer methods now receive List<ReactionEvent>
        factory.setBatchListener(true);

        // Commit offsets only after the entire batch has been processed successfully.
        // Prevents partial batch commits which would silently lose events on re-delivery.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);

        // 12 concurrent listener threads — matches the 12 partitions on the topic.
        // Each thread owns one partition, ensuring ordered processing per partition key (postId).
        factory.setConcurrency(12);

        return factory;
    }
}
