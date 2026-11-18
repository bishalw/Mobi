package com.mobi.auth.event;

import java.time.LocalDateTime;
import java.util.UUID;

// Carries enough of the account for a consumer to keep its own read-only copy without
// calling back into this service. createdAt is the signup time from the user row, which
// is not the same as occurredAt: a role change today describes an account that signed up
// months ago, and a projection needs the former.
public class UserEvent {

    private String eventId;
    private UserEventType eventType;
    private Long userId;
    private String username;
    private String email;
    private String phone;
    private String role;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime occurredAt;

    public UserEvent() {}

    public UserEvent(UserEventType eventType, Long userId, String username) {
        this(eventType, userId, username, null, null, true, null);
    }

    public UserEvent(UserEventType eventType, Long userId, String username, String email,
                     String role, boolean enabled, LocalDateTime createdAt) {
        this(eventType, userId, username, email, null, role, enabled, createdAt);
    }

    public UserEvent(UserEventType eventType, Long userId, String username, String email, String phone,
                     String role, boolean enabled, LocalDateTime createdAt) {
        this.phone = phone;
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
        this.enabled = enabled;
        this.createdAt = createdAt;
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

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
