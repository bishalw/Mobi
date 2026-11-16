package com.mobi.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One charge attempt. Card details never reach this table: the client sends a
 * provider token, and only the brand and last four digits the provider reports
 * back are kept, which is all a receipt or a support agent needs.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Public identifier. The numeric id stays internal so payment volume cannot be
    // read off sequential ids.
    @Column(nullable = false, unique = true, length = 36)
    private String paymentId;

    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PROCESSING;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(length = 100)
    private String providerReference;

    @Column(length = 20)
    private String cardBrand;

    @Column(length = 4)
    private String cardLast4;

    @Column(length = 50)
    private String failureCode;

    @Column(length = 255)
    private String failureMessage;

    @Column(length = 255)
    private String refundReason;

    @Column
    private LocalDateTime refundedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        if (paymentId == null) {
            paymentId = UUID.randomUUID().toString();
        }
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void markSucceeded(String providerReference, String cardBrand, String cardLast4) {
        this.status = PaymentStatus.SUCCEEDED;
        this.providerReference = providerReference;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
        this.failureCode = null;
        this.failureMessage = null;
    }

    public void markDeclined(String providerReference, String failureCode, String failureMessage) {
        this.status = PaymentStatus.DECLINED;
        this.providerReference = providerReference;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public void markRefunded(String reason) {
        this.status = PaymentStatus.REFUNDED;
        this.refundReason = reason;
        this.refundedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getPaymentId() { return paymentId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public PaymentStatus getStatus() { return status; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderReference() { return providerReference; }
    public String getCardBrand() { return cardBrand; }
    public String getCardLast4() { return cardLast4; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public String getRefundReason() { return refundReason; }
    public LocalDateTime getRefundedAt() { return refundedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
