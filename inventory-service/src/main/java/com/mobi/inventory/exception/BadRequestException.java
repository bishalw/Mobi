package com.mobi.inventory.exception;

/** A request that is well-formed but breaks a business rule the caller can fix. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
