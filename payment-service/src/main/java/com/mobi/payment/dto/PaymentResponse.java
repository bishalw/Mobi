package com.mobi.payment.dto;

import com.mobi.payment.entity.Payment;
import com.mobi.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        String paymentId,
        Long orderId,
        Long userId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String provider,
        String cardBrand,
        String cardLast4,
        String failureCode,
        String failureMessage,
        String refundReason,
        LocalDateTime refundedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getPaymentId(), payment.getOrderId(), payment.getUserId(),
                payment.getStatus(), payment.getAmount(), payment.getCurrency(), payment.getProvider(),
                payment.getCardBrand(), payment.getCardLast4(), payment.getFailureCode(),
                payment.getFailureMessage(), payment.getRefundReason(), payment.getRefundedAt(),
                payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
