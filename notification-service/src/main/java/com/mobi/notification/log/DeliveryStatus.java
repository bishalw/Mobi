package com.mobi.notification.log;

public enum DeliveryStatus {
    SENT,
    /** Nothing to send to: no contact on file, or the account is disabled. */
    SKIPPED
}
