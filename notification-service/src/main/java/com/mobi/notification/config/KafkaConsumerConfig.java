package com.mobi.notification.config;

import com.mobi.notification.event.InventoryEvent;
import com.mobi.notification.event.OrderEvent;
import com.mobi.notification.event.ProcurementEvent;
import com.mobi.notification.event.PaymentEvent;
import com.mobi.notification.event.UserEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
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

/**
 * One container factory per event type, all sharing the same error handling:
 *
 * <ul>
 *   <li>Malformed messages are caught by {@link ErrorHandlingDeserializer} instead of
 *       failing inside poll() and blocking the partition forever.</li>
 *   <li>Failures are retried in place with exponential backoff, which rides out a mail
 *       server restart or a database blip.</li>
 *   <li>Records that still fail are published to {@code <topic>.DLT} with the
 *       exception in the headers, and the partition moves on.</li>
 * </ul>
 */
@Configuration
public class KafkaConsumerConfig {

    @Autowired
    private KafkaClientSettings kafkaClientSettings;

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    private final String groupId;

    public KafkaConsumerConfig(@Value("${spring.kafka.consumer.group-id}") String groupId) {
        this.groupId = groupId;
    }

    @Bean
    public KafkaTemplate<String, Object> deadLetterKafkaTemplate() {
        Map<String, Object> config = kafkaClientSettings.base();
        Map<Class<?>, Serializer<?>> delegates = new LinkedHashMap<>();
        // A record that failed deserialization reaches the recoverer as its original
        // bytes and is written to the DLT unchanged, ready to be replayed after a fix.
        delegates.put(byte[].class, new ByteArraySerializer());
        JsonSerializer<Object> json = new JsonSerializer<>();
        json.setAddTypeInfo(false);
        delegates.put(Object.class, json);
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                config, new StringSerializer(), new DelegatingByTypeSerializer(delegates, true)));
        template.setObservationEnabled(true);
        return template;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> deadLetterKafkaTemplate,
            @Value("${kafka.retry.initial-interval-ms:2000}") long initialIntervalMs,
            @Value("${kafka.retry.multiplier:2.0}") double multiplier,
            @Value("${kafka.retry.max-interval-ms:30000}") long maxIntervalMs,
            @Value("${kafka.retry.max-elapsed-ms:120000}") long maxElapsedMs) {
        ExponentialBackOff backOff = new ExponentialBackOff(initialIntervalMs, multiplier);
        backOff.setMaxInterval(maxIntervalMs);
        backOff.setMaxElapsedTime(maxElapsedMs);
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate), backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class, NullPointerException.class);
        handler.setRetryListeners((record, ex, attempt) -> log.warn(
                "Kafka delivery attempt {} failed for {}-{}@{}: {}",
                attempt, record.topic(), record.partition(), record.offset(), ex.getMessage()));
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> orderEventKafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        return factory(OrderEvent.class, kafkaErrorHandler);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserEvent> userEventKafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        return factory(UserEvent.class, kafkaErrorHandler);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> paymentEventKafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        return factory(PaymentEvent.class, kafkaErrorHandler);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProcurementEvent> procurementEventKafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        return factory(ProcurementEvent.class, kafkaErrorHandler);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryEvent> inventoryEventKafkaListenerContainerFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        return factory(InventoryEvent.class, kafkaErrorHandler);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> factory(Class<T> type, DefaultErrorHandler errorHandler) {
        Map<String, Object> config = kafkaClientSettings.base();
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, type.getName());
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        factory.setCommonErrorHandler(errorHandler);
        // Joins the trace carried in the record's traceparent header.
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }
}
