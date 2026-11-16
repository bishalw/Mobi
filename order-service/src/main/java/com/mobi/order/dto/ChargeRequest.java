package com.mobi.order.dto;

import java.math.BigDecimal;

public record ChargeRequest(
        Long orderId,
        Long userId,
        BigDecimal amount,
        String currency,
        String paymentMethodToken,
        String idempotencyKey) {
}
