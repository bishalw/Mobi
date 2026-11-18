package com.mobi.auth.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mobi.auth.entity.User;
import com.mobi.auth.outbox.OutboxEvent;
import com.mobi.auth.outbox.OutboxEventRepository;
import com.mobi.auth.outbox.OutboxTracing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UserEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final String userEventsTopic;
    private final OutboxTracing outboxTracing;

    public UserEventPublisher(OutboxEventRepository outboxEventRepository,
                              @Value("${users.kafka.topic}") String userEventsTopic,
                              OutboxTracing outboxTracing) {
        this.outboxTracing = outboxTracing;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.userEventsTopic = userEventsTopic;
    }

    public void publish(UserEventType eventType, User user) {
        UserEvent event = new UserEvent(eventType, user.getId(), user.getUsername(), user.getEmail(),
                user.getPhone(), user.getRole(), user.isEnabled(), user.getCreatedAt());
        try {
            OutboxEvent outboxEvent = new OutboxEvent(
                    userEventsTopic,
                    String.valueOf(user.getId()),
                    String.valueOf(user.getId()),
                    eventType.name(),
                    objectMapper.writeValueAsString(event)
            );
            outboxEvent.setTraceparent(outboxTracing.currentTraceparent());
            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException exp) {
            throw new IllegalStateException("Unable to serialize user event", exp);
        }
    }
}
