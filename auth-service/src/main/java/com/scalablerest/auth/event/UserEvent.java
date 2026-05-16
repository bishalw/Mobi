package com.scalablerest.auth.event;

import java.time.LocalDateTime;
import java.util.UUID;

// username only — no email field exists on User yet; revisit when adding email support
public class UserEvent {

    private String eventId;
    private UserEventType eventType;
    private Long userId;
    private String username;
    private LocalDateTime occurredAt;

    public UserEvent() {}

    public UserEvent(UserEventType eventType, Long userId, String username) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.userId = userId;
        this.username = username;
        this.occurredAt = LocalDateTime.now();
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public UserEventType getEventType() { return eventType; }
    public void setEventType(UserEventType eventType) { this.eventType = eventType; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
