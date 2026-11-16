package com.mobi.notification.sender;

/**
 * A message could not be delivered. {@code permanent} means retrying can't help (the mail
 * server rejected the address); anything else is retried by the Kafka error handler.
 */
public class DeliveryException extends RuntimeException {

    private final boolean permanent;

    public DeliveryException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public DeliveryException(String message, Throwable cause, boolean permanent) {
        super(message, cause);
        this.permanent = permanent;
    }

    public DeliveryException(String message) {
        super(message);
        this.permanent = false;
    }

    public boolean isPermanent() {
        return permanent;
    }
}
