package com.mobi.notification.event;

import java.time.LocalDateTime;

public class UserEvent {

    private String eventId;
    private UserEventType eventType;
    private Long userId;
    private String username;
    private LocalDateTime occurredAt;

    public UserEvent() {}

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
