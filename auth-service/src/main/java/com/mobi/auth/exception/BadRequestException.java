package com.mobi.auth.exception;

/** A request that is well-formed JSON but asks for something not allowed (400). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
