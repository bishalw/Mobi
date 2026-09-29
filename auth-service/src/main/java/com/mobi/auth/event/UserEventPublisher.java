package com.mobi.auth.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mobi.auth.entity.User;
import com.mobi.auth.outbox.OutboxEvent;
import com.mobi.auth.outbox.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UserEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final String userEventsTopic;

    public UserEventPublisher(OutboxEventRepository outboxEventRepository,
                              @Value("${users.kafka.topic}") String userEventsTopic) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.userEventsTopic = userEventsTopic;
    }

    public void publish(UserEventType eventType, User user) {
        UserEvent event = new UserEvent(eventType, user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), user.isEnabled(), user.getCreatedAt());
        try {
            outboxEventRepository.save(new OutboxEvent(
                    userEventsTopic,
                    String.valueOf(user.getId()),
                    String.valueOf(user.getId()),
                    eventType.name(),
                    objectMapper.writeValueAsString(event)
            ));
        } catch (JsonProcessingException exp) {
            throw new IllegalStateException("Unable to serialize user event", exp);
        }
    }
}
