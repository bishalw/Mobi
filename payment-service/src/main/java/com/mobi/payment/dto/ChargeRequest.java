package com.mobi.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Sent by order-service only. The amount comes from the order, never from the
 * browser, which is why this endpoint is internal: a public charge endpoint would let
 * a customer name their own price.
 */
public record ChargeRequest(
        @NotNull Long orderId,
        @NotNull Long userId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotBlank @Size(max = 100) String paymentMethodToken,
        @NotBlank @Size(max = 100) String idempotencyKey) {
}
