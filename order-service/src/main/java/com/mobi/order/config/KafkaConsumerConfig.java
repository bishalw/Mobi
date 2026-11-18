package com.mobi.order.config;

import com.mobi.order.event.UserEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

// This service produced events but never consumed any until the user projection needed
// them, so the consumer side is configured here from scratch.
@Configuration
public class KafkaConsumerConfig {

    @Autowired
    private KafkaClientSettings kafkaClientSettings;

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public ConsumerFactory<String, UserEvent> userEventConsumerFactory(
            @Value("${spring.kafka.consumer.group-id}") String groupId) {
        Map<String, Object> config = kafkaClientSettings.base();
        // A group of its own, distinct from notification-service's. Sharing a group id
        // would make the two services split the partitions between them, so each would
        // see only part of the stream and the projection would silently lose users.
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // Reads the topic from the beginning on first start, which backfills every user
        // still inside Kafka's retention window without any manual step.
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // ErrorHandlingDeserializer wraps the JSON one so a malformed message (a "poison
        // pill") becomes a DeserializationException the error handler can route to the
        // DLT. Without it the deserializer throws inside poll(), before any handler runs,
        // and the consumer retries the same offset forever, blocking the partition.
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, UserEvent.class.getName());
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Publishes records that exhausted their retries to {@code <topic>.DLT}, same
     * partition, with the exception class, message and stack trace in headers.
     *
     * <p>Values are serialised by type: a record that failed deserialization arrives
     * here as its original raw bytes and is written back byte-for-byte, so the DLT
     * holds exactly what was on the source topic and can be replayed after a fix.
     */
    @Bean
    public KafkaTemplate<String, Object> deadLetterKafkaTemplate() {
        Map<String, Object> config = kafkaClientSettings.base();
        Map<Class<?>, org.apache.kafka.common.serialization.Serializer<?>> delegates = new LinkedHashMap<>();
        delegates.put(byte[].class, new ByteArraySerializer());
        JsonSerializer<Object> json = new JsonSerializer<>();
        json.setAddTypeInfo(false);
        delegates.put(Object.class, json);
        DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(
                config, new StringSerializer(), new DelegatingByTypeSerializer(delegates, true));
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(factory);
        template.setObservationEnabled(true);
        return template;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> deadLetterKafkaTemplate,
            @Value("${kafka.retry.initial-interval-ms:1000}") long initialIntervalMs,
            @Value("${kafka.retry.multiplier:2.0}") double multiplier,
            @Value("${kafka.retry.max-interval-ms:10000}") long maxIntervalMs,
            @Value("${kafka.retry.max-elapsed-ms:30000}") long maxElapsedMs) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate);
        // 1s, 2s, 4s, 8s, 10s... capped at 30s in total. Long enough to ride out a
        // database failover, short enough that one bad record does not stall the
        // partition for minutes. Retries are in-memory and block only this partition.
        ExponentialBackOff backOff = new ExponentialBackOff(initialIntervalMs, multiplier);
        backOff.setMaxInterval(maxIntervalMs);
        backOff.setMaxElapsedTime(maxElapsedMs);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // Retrying cannot fix these; send them straight to the DLT. Deserialization and
        // conversion failures are already non-retryable by default.
        handler.addNotRetryableExceptions(IllegalArgumentException.class, NullPointerException.class);
        handler.setRetryListeners((record, ex, attempt) -> log.warn(
                "Kafka delivery attempt {} failed for {}-{}@{}: {}",
                attempt, record.topic(), record.partition(), record.offset(), ex.getMessage()));
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserEvent> userEventKafkaListenerContainerFactory(
            ConsumerFactory<String, UserEvent> userEventConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, UserEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(userEventConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        // Continues the producer's trace from the traceparent header, so a consumed
        // event shows up as a child span of the request that caused it.
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }
}
