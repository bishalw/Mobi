package com.mobi.notification.sender;

public interface SmsSender {

    /** @param to an E.164 number, e.g. +15551234567 */
    void send(String to, String body);
}
