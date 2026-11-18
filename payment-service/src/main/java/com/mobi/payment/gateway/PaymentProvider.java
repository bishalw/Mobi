package com.mobi.payment.gateway;

import java.math.BigDecimal;

/**
 * The card network, behind one seam. PaymentService never knows which provider is
 * live; {@code payments.provider} picks the bean.
 *
 * <p>Both operations must be idempotent on {@code idempotencyKey}: a retry after a
 * timeout has to return the first outcome rather than move money twice. Real
 * providers (Stripe's Idempotency-Key header, Adyen's idempotency-key) offer exactly
 * this, and the simulated one reproduces it.
 */
public interface PaymentProvider {

    String name();

    ChargeOutcome charge(ChargeCommand command);

    RefundOutcome refund(String providerReference, BigDecimal amount, String currency, String idempotencyKey);

    record ChargeCommand(BigDecimal amount, String currency, String paymentMethodToken,
                         String idempotencyKey, String description) {
    }

    record ChargeOutcome(boolean approved, String providerReference, String cardBrand, String cardLast4,
                         String failureCode, String failureMessage) {

        public static ChargeOutcome approved(String reference, String brand, String last4) {
            return new ChargeOutcome(true, reference, brand, last4, null, null);
        }

        public static ChargeOutcome declined(String reference, String code, String message) {
            return new ChargeOutcome(false, reference, null, null, code, message);
        }
    }

    record RefundOutcome(String refundReference) {
    }
}
