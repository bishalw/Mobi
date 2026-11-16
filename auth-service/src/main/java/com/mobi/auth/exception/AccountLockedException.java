package com.mobi.auth.exception;

import java.time.Duration;

public class AccountLockedException extends RuntimeException {

    public AccountLockedException(Duration remaining) {
        super("Too many failed sign-in attempts. Try again in "
                + Math.max(1, (remaining.toSeconds() + 59) / 60) + " minute(s).");
    }
}
