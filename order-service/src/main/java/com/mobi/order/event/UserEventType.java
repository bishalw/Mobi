package com.mobi.order.event;

// Must list every type auth-service can publish, including those this service ignores:
// Jackson fails the entire message on an unrecognised enum value, so a missing constant
// does not skip the event, it breaks the listener for that record.
public enum UserEventType {
    USER_REGISTERED,
    USER_ROLE_CHANGED,
    USER_DISABLED,
    USER_ENABLED,
    USER_SNAPSHOT
}
