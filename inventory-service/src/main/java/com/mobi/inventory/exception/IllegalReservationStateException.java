package com.mobi.inventory.exception;

public class IllegalReservationStateException extends RuntimeException {
    public IllegalReservationStateException(String message) {
        super(message);
    }
}
