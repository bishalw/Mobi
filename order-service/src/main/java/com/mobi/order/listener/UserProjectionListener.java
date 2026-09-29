package com.mobi.order.listener;

import com.mobi.order.entity.UserRef;
import com.mobi.order.event.UserEvent;
import com.mobi.order.repository.UserRefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// Maintains the local copy of auth-service's accounts. Every event type is treated as
// "here is the current state of this user" and upserted, so the projection converges no
// matter which events a consumer happens to see or in which order.
@Component
public class UserProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(UserProjectionListener.class);

    private final UserRefRepository userRefRepository;

    public UserProjectionListener(UserRefRepository userRefRepository) {
        this.userRefRepository = userRefRepository;
    }

    @KafkaListener(
            topics = "${users.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "userEventKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleUserEvent(UserEvent event) {
        if (event == null || event.getUserId() == null || event.getUsername() == null) {
            log.warn("Skipping unusable user event: {}", event);
            return;
        }

        LocalDateTime occurredAt = event.getOccurredAt() == null ? LocalDateTime.now() : event.getOccurredAt();
        UserRef existing = userRefRepository.findById(event.getUserId()).orElse(null);

        // Redelivery and cross-partition reordering both mean an older event can arrive
        // after a newer one. Without this guard, a replayed snapshot could overwrite a
        // more recent role change or suspension with stale values.
        if (existing != null && existing.getUpdatedAt() != null && existing.getUpdatedAt().isAfter(occurredAt)) {
            log.debug("Ignoring stale {} for user {}", event.getEventType(), event.getUserId());
            return;
        }

        UserRef userRef = existing == null ? new UserRef() : existing;
        userRef.setUserId(event.getUserId());
        userRef.setUsername(event.getUsername());
        userRef.setEmail(event.getEmail());
        userRef.setEnabled(event.isEnabled());
        // Older events predate createdAt being carried on the payload. Falling back to the
        // event time keeps the NOT NULL column satisfied without inventing a signup date
        // for a row that already has a better one.
        userRef.setCreatedAt(resolveCreatedAt(event, existing, occurredAt));
        userRef.setUpdatedAt(occurredAt);

        userRefRepository.save(userRef);
        log.debug("Projected {} for user {}", event.getEventType(), event.getUserId());
    }

    private LocalDateTime resolveCreatedAt(UserEvent event, UserRef existing, LocalDateTime occurredAt) {
        if (event.getCreatedAt() != null) {
            return event.getCreatedAt();
        }
        if (existing != null && existing.getCreatedAt() != null) {
            return existing.getCreatedAt();
        }
        return occurredAt;
    }
}
