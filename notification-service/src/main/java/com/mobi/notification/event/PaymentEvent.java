package com.mobi.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentEvent(
        String eventId,
        PaymentEventType eventType,
        String paymentId,
        Long orderId,
        Long userId,
        BigDecimal amount,
        String currency,
        String cardBrand,
        String cardLast4,
        String failureCode,
        String failureMessage,
        LocalDateTime occurredAt) {
}
