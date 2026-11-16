package com.mobi.procurement.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * The outbox already holds each event as JSON, so records are sent as that JSON text.
 * On the wire this is the same as a JsonSerializer would produce, without type headers.
 */
@Configuration
public class KafkaProducerConfig {

    @Autowired
    private KafkaClientSettings kafkaClientSettings;

    @Bean
    public ProducerFactory<String, String> procurementEventProducerFactory() {
        Map<String, Object> config = kafkaClientSettings.base();
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // A lost GOODS_RECEIVED would leave counted stock off the shelf.
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> procurementEventKafkaTemplate(
            ProducerFactory<String, String> procurementEventProducerFactory) {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(procurementEventProducerFactory);
        template.setObservationEnabled(true);
        return template;
    }
}
