package com.mobi.inventory.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Records travel as JSON text both ways. The listener parses the payload itself, so a
 * malformed record surfaces as an exception in the handler, where the error handler can
 * route it to {@code <topic>.DLT} instead of blocking the partition.
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    private final Map<String, Object> common;

    public KafkaConfig(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                       @Value("${spring.kafka.security.protocol:PLAINTEXT}") String securityProtocol,
                       @Value("${spring.kafka.properties.sasl.mechanism:PLAIN}") String saslMechanism,
                       @Value("${spring.kafka.properties.sasl.jaas.config:}") String saslJaasConfig) {
        common = new HashMap<>();
        common.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        common.put("security.protocol", securityProtocol);
        if (!saslJaasConfig.isBlank()) {
            common.put("sasl.mechanism", saslMechanism);
            common.put("sasl.jaas.config", saslJaasConfig);
        }
    }

    /** Used by the outbox publisher and, for exhausted records, the dead-letter recoverer. */
    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        Map<String, Object> config = new HashMap<>(common);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        KafkaTemplate<String, String> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
        template.setObservationEnabled(true);
        return template;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> stringKafkaTemplate,
            @Value("${kafka.retry.initial-interval-ms:1000}") long initialIntervalMs,
            @Value("${kafka.retry.max-elapsed-ms:900000}") long maxElapsedMs) {
        ExponentialBackOff backOff = new ExponentialBackOff(initialIntervalMs, 2.0);
        backOff.setMaxInterval(10_000);
        backOff.setMaxElapsedTime(maxElapsedMs);
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(stringKafkaTemplate), backOff);
        // A payload that can't be parsed won't parse on a retry either.
        handler.addNotRetryableExceptions(IllegalArgumentException.class, NullPointerException.class);
        handler.setRetryListeners((record, ex, attempt) -> log.warn("Kafka delivery attempt {} failed for {}-{}@{}: {}",
                attempt, record.topic(), record.partition(), record.offset(), ex.getMessage()));
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> procurementEventListenerFactory(
            DefaultErrorHandler kafkaErrorHandler,
            @Value("${inventory.kafka.consumer-group}") String groupId) {
        Map<String, Object> config = new HashMap<>(common);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }
}
