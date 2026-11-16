package com.mobi.payment.gateway;

/**
 * The provider could not give an answer: unreachable, timed out, or failed on its
 * side. Distinct from a decline, which is a definite answer. The charge may or may not
 * have happened, so the payment stays PROCESSING and the caller retries with the same
 * idempotency key to find out.
 */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message) {
        super(message);
    }

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
