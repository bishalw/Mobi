package com.mobi.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentResult(
        String paymentId,
        Long orderId,
        String status,
        BigDecimal amount,
        String currency,
        String failureCode,
        String failureMessage) {

    public boolean succeeded() {
        return "SUCCEEDED".equals(status);
    }

    public boolean declined() {
        return "DECLINED".equals(status);
    }
}
