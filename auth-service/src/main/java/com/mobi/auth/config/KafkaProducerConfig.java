package com.mobi.auth.config;

import com.mobi.auth.event.UserEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Autowired
    private KafkaClientSettings kafkaClientSettings;

    @Bean
    public ProducerFactory<String, UserEvent> userEventProducerFactory() {
        Map<String, Object> config = kafkaClientSettings.base();
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, UserEvent> userEventKafkaTemplate(
            ProducerFactory<String, UserEvent> userEventProducerFactory) {
        KafkaTemplate<String, UserEvent> template = new KafkaTemplate<>(userEventProducerFactory);
        template.setObservationEnabled(true);
        return template;
    }
}
