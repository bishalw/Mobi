package com.mobi.payment.entity;

public enum PaymentStatus {
    /** Row written, provider not yet answered. Claims the idempotency key. */
    PROCESSING,
    SUCCEEDED,
    DECLINED,
    REFUNDED
}
