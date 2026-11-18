package com.mobi.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

// A consumer-side copy of the producer's event, deliberately narrower: this service only
// needs to know who registered. ignoreUnknown makes that asymmetry safe — auth-service can
// add fields to the payload without this deserializer rejecting every message.
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserEvent {

    private String eventId;
    private UserEventType eventType;
    private Long userId;
    private String username;
    private String email;
    private String phone;
    private String role;
    private Boolean enabled;
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

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
