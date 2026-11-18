package com.mobi.notification.sender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default SMS "provider": writes the message to the log. SMS has no local equivalent
 * of Mailpit and every real provider charges per message, so nothing is sent unless
 * notifications.sms.provider=twilio is set deliberately.
 */
@Component
@ConditionalOnProperty(name = "notifications.sms.provider", havingValue = "log", matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void send(String to, String body) {
        log.info("SMS to {}: {}", mask(to), body);
    }

    private static String mask(String number) {
        return number.length() <= 4 ? "****" : "*".repeat(number.length() - 4) + number.substring(number.length() - 4);
    }
}
