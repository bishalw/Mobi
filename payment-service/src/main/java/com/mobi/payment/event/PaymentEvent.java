package com.mobi.payment.event;

import com.mobi.payment.entity.Payment;
import com.mobi.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentEvent(
        String eventId,
        PaymentEventType eventType,
        String paymentId,
        Long orderId,
        Long userId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String cardBrand,
        String cardLast4,
        String failureCode,
        String failureMessage,
        LocalDateTime occurredAt) {

    public static PaymentEvent of(PaymentEventType type, Payment payment) {
        return new PaymentEvent(UUID.randomUUID().toString(), type, payment.getPaymentId(), payment.getOrderId(),
                payment.getUserId(), payment.getStatus(), payment.getAmount(), payment.getCurrency(),
                payment.getCardBrand(), payment.getCardLast4(), payment.getFailureCode(),
                payment.getFailureMessage(), LocalDateTime.now());
    }
}
