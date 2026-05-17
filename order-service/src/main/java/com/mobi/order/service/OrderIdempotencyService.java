package com.mobi.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mobi.order.dto.OrderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class OrderIdempotencyService {

    private static final String KEY_PREFIX = "order:idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public OrderIdempotencyService(StringRedisTemplate redisTemplate,
                                   @Value("${order.idempotency.ttl-seconds}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<OrderResponse> get(String idempotencyKey) {
        if (isBlank(idempotencyKey)) {
            return Optional.empty();
        }
        String cachedValue = redisTemplate.opsForValue().get(toRedisKey(idempotencyKey));
        if (cachedValue == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(cachedValue, OrderResponse.class));
        } catch (JsonProcessingException exp) {
            throw new IllegalStateException("Unable to deserialize cached order response", exp);
        }
    }

    public void save(String idempotencyKey, OrderResponse response) {
        if (isBlank(idempotencyKey)) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(toRedisKey(idempotencyKey), objectMapper.writeValueAsString(response), ttl);
        } catch (JsonProcessingException exp) {
            throw new IllegalStateException("Unable to serialize order response", exp);
        }
    }

    private String toRedisKey(String idempotencyKey) {
        return KEY_PREFIX + idempotencyKey.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
