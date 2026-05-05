package curious.sync.configurations.Kafka;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import curious.sync.models.Events.ReactionEvent;

@Configuration
public class KafkaProducerConfig {

    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    ProducerFactory<String, ReactionEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Batching
        config.put(ProducerConfig.LINGER_MS_CONFIG, 20);

        // Batch size
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 65536);

        // Compression
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

        // Durability
        config.put(ProducerConfig.ACKS_CONFIG, "1");

        // Up to 5 in-flight batches per connection (safe with acks=1).
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    KafkaTemplate<String, ReactionEvent> getKafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
