package curious.sync.configurations.Kafka;

import static curious.sync.constants.Strings.LIKES_PROCESSOR_GROUP;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import org.springframework.beans.factory.annotation.Value;

import curious.sync.models.Events.ReactionEvent;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.consumer.max-poll-records}")
    private int maxPollRecords;

    @Value("${kafka.consumer.fetch-min-bytes}")
    private int fetchMinBytes;

    @Value("${kafka.consumer.fetch-max-wait-ms}")
    private int fetchMaxWaitMs;

    @Value("${kafka.consumer.max-partition-fetch-bytes}")
    private int maxPartitionFetchBytes;

    @Value("${kafka.consumer.fetch-max-bytes}")
    private int fetchMaxBytes;

    @Value("${kafka.consumer.max-poll-interval-ms}")
    private int maxPollIntervalMs;

    @Value("${kafka.consumer.concurrency}")
    private int concurrency;

    @Value("${kafka.consumer.retry.backoff-ms}")
    private long retryBackoffMs;

    @Value("${kafka.consumer.retry.max-attempts}")
    private long retryMaxAttempts;

    @Bean
    ConsumerFactory<Long, ReactionEvent> consumerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, LIKES_PROCESSOR_GROUP);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, fetchMinBytes);
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, fetchMaxWaitMs);
        config.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, maxPartitionFetchBytes);
        config.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, fetchMaxBytes);

        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);

        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        JsonDeserializer<ReactionEvent> deserializer = new JsonDeserializer<>(ReactionEvent.class);
        deserializer.addTrustedPackages("curious.sync.models.Events.*");
        deserializer.setUseTypeMapperForKey(false);

        ErrorHandlingDeserializer<ReactionEvent> errorHandlingDeserializer = 
                new ErrorHandlingDeserializer<>(deserializer);

        return new DefaultKafkaConsumerFactory<>(config, new LongDeserializer(), errorHandlingDeserializer);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<Long, ReactionEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<Long, ReactionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());
        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setConcurrency(concurrency);

        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(retryBackoffMs, retryMaxAttempts)));

        return factory;
    }
}