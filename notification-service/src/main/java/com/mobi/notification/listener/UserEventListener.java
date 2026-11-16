package com.mobi.notification.listener;

import com.mobi.notification.contact.UserContact;
import com.mobi.notification.contact.UserContactRepository;
import com.mobi.notification.event.UserEvent;
import com.mobi.notification.event.UserEventType;
import com.mobi.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class UserEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);

    private final UserContactRepository contacts;
    private final NotificationDispatcher dispatcher;

    public UserEventListener(UserContactRepository contacts, NotificationDispatcher dispatcher) {
        this.contacts = contacts;
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            topics = "${users.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "userEventKafkaListenerContainerFactory"
    )
    public void handleUserEvent(UserEvent event) {
        if (event == null || event.getEventType() == null || event.getUserId() == null) {
            log.warn("Received unusable user event, skipping. payload={}", event);
            return;
        }
        log.info("Received {} for user {}", event.getEventType(), event.getUserId());
        project(event);

        // The welcome email is specific to registration. USER_SNAPSHOT republishes the
        // same account for rebuilds and must not greet anyone a second time.
        if (event.getEventType() == UserEventType.USER_REGISTERED) {
            dispatcher.email(event.getEventId(), event.getUserId(), "welcome", "Welcome to Mobi", Map.of());
        }
    }

    // Every event type is the latest known state of the account, upserted. Older
    // events arriving late are ignored so they cannot undo a newer change.
    private void project(UserEvent event) {
        LocalDateTime occurredAt = event.getOccurredAt() == null ? LocalDateTime.now() : event.getOccurredAt();
        UserContact contact = contacts.findById(event.getUserId()).orElseGet(UserContact::new);
        if (contact.getUpdatedAt() != null && contact.getUpdatedAt().isAfter(occurredAt)) {
            return;
        }
        contact.setUserId(event.getUserId());
        contact.setUsername(event.getUsername() == null ? "customer" : event.getUsername());
        contact.setEmail(event.getEmail());
        contact.setPhone(event.getPhone());
        // Events from before role was on the payload leave the last known role alone.
        if (event.getRole() != null) {
            contact.setRole(event.getRole());
        }
        contact.setEnabled(isEnabled(event));
        contact.setUpdatedAt(occurredAt);
        contacts.save(contact);
    }

    // Events published before `enabled` was on the payload default to enabled.
    private static boolean isEnabled(UserEvent event) {
        return event.getEnabled() == null || event.getEnabled();
    }
}
