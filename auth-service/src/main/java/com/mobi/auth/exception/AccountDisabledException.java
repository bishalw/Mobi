package com.mobi.auth.exception;

public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("This account has been disabled");
    }
}
