package com.mobi.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A tokenised payment method, never a card number. The browser hands card details
 * straight to the payment provider and gets back a token like {@code tok_visa}; only
 * the token passes through Mobi, which keeps every service here out of PCI scope.
 */
public record PayOrderRequest(
        @NotBlank(message = "paymentMethodToken is required")
        @Size(max = 100)
        String paymentMethodToken) {
}
