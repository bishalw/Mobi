package com.mobi.notification.event;

// Every type payment-service can publish; see the note on UserEventType.
public enum PaymentEventType {
    PAYMENT_SUCCEEDED,
    PAYMENT_DECLINED,
    PAYMENT_REFUNDED
}
