package com.mobi.auth.event;

public enum UserEventType {
    USER_REGISTERED,
    USER_ROLE_CHANGED,
    USER_DISABLED,
    USER_ENABLED,
    // Current state of an account, republished on demand so a consumer can rebuild its
    // projection from scratch. Distinct from USER_REGISTERED precisely so that a rebuild
    // does not look like a fresh signup and re-trigger welcome emails.
    USER_SNAPSHOT
}
