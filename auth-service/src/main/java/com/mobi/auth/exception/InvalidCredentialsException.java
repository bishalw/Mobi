package com.mobi.auth.exception;

/**
 * Wrong username or password. Deliberately one exception for both, so the response
 * does not reveal which usernames exist.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
