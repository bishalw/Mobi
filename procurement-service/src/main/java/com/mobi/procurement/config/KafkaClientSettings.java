package com.mobi.procurement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Connection settings shared by every Kafka client this service builds by hand. Without
 * Spring Boot's Kafka auto-configuration the spring.kafka.* properties are not applied
 * automatically, so the security ones (SASL on a managed cluster) are copied in here.
 */
@Component
public class KafkaClientSettings {

    private final Map<String, Object> base = new HashMap<>();

    public KafkaClientSettings(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                               @Value("${spring.kafka.security.protocol:PLAINTEXT}") String securityProtocol,
                               @Value("${spring.kafka.properties.sasl.mechanism:PLAIN}") String saslMechanism,
                               @Value("${spring.kafka.properties.sasl.jaas.config:}") String saslJaasConfig) {
        base.put("bootstrap.servers", bootstrapServers);
        base.put("security.protocol", securityProtocol);
        if (!saslJaasConfig.isBlank()) {
            base.put("sasl.mechanism", saslMechanism);
            base.put("sasl.jaas.config", saslJaasConfig);
        }
    }

    /** A fresh, mutable copy: bootstrap servers plus security settings. */
    public Map<String, Object> base() {
        return new HashMap<>(base);
    }
}
