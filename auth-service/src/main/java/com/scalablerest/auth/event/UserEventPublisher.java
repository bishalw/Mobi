package com.scalablerest.auth.event;

import com.scalablerest.auth.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserEventPublisher {

    private final KafkaTemplate<String, UserEvent> kafkaTemplate;
    private final String userEventsTopic;

    public UserEventPublisher(KafkaTemplate<String, UserEvent> kafkaTemplate,
                              @Value("${users.kafka.topic}") String userEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.userEventsTopic = userEventsTopic;
    }

    public void publish(UserEventType eventType, User user) {
        UserEvent event = new UserEvent(eventType, user.getId(), user.getUsername());
        kafkaTemplate.send(userEventsTopic, String.valueOf(user.getId()), event);
    }
}
