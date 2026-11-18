package com.mobi.notification.log;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One delivered (or deliberately skipped) message. The unique key on
 * (event_id, channel) is the idempotency guard: Kafka delivers at least once, so the
 * same event can arrive again after a rebalance or a retry, and without this the
 * customer would get the same email twice.
 */
@Entity
@Table(name = "notifications")
public class NotificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Channel channel;

    @Column(nullable = false, length = 50)
    private String template;

    @Column
    private Long userId;

    @Column(length = 255)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeliveryStatus status;

    @Column(length = 255)
    private String detail;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected NotificationRecord() {
    }

    public NotificationRecord(String eventId, Channel channel, String template, Long userId,
                              String recipient, DeliveryStatus status, String detail) {
        this.eventId = eventId;
        this.channel = channel;
        this.template = template;
        this.userId = userId;
        this.recipient = recipient;
        this.status = status;
        this.detail = detail;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public String getEventId() { return eventId; }
    public Channel getChannel() { return channel; }
    public String getTemplate() { return template; }
    public String getRecipient() { return recipient; }
    public DeliveryStatus getStatus() { return status; }
    public String getDetail() { return detail; }
}
