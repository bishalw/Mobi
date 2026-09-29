package com.mobi.auth.exception;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long userId) {
        super("No user with id " + userId);
    }
}
