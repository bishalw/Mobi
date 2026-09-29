package com.mobi.notification.listener;

import com.mobi.notification.event.UserEvent;
import com.mobi.notification.event.UserEventType;
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

        // The welcome email is specific to registration. This used to run for every event
        // on the topic, which was harmless while USER_REGISTERED was the only type and
        // would have greeted people on being suspended once it wasn't.
        if (event.getEventType() == UserEventType.USER_REGISTERED) {
            log.info("Sending welcome email to user {} ({})", event.getUserId(), event.getUsername());
        }
    }
}
