package com.mobi.notification.event;

// Must list every type auth-service can publish, even the ones this service ignores.
// Jackson fails the whole message on an unrecognised enum value, so an event type
// missing from here does not get skipped — it poisons the listener for that record.
public enum UserEventType {
    USER_REGISTERED,
    USER_ROLE_CHANGED,
    USER_DISABLED,
    USER_ENABLED,
    USER_SNAPSHOT
}
