package com.mobi.notification.listener;

import com.mobi.notification.event.UserEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);

    @KafkaListener(
            topics = "${users.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "userEventKafkaListenerContainerFactory"
    )
    public void handleUserEvent(UserEvent event) {
        if (event == null || event.getEventType() == null) {
            log.warn("Received user event with missing type, skipping. payload={}", event);
            return;
        }
        log.info("Received {} for user {}", event.getEventType(), event.getUserId());
        log.info("Sending welcome email to user {} ({})", event.getUserId(), event.getUsername());
    }
}
