package com.mobi.notification.catalog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Staff alerts (purchase orders, low stock) are only useful while fresh. On a first start
 * the consumer reads its topics from the beginning; without this, every old PO would be
 * emailed again. Events without a readable timestamp are treated as fresh.
 */
@Component
public class EventAge {

    private final Duration maxAge;
    private final Clock clock;

    @Autowired
    public EventAge(@Value("${notifications.staff.max-event-age:PT24H}") Duration maxAge) {
        this(maxAge, Clock.systemUTC());
    }

    public EventAge(Duration maxAge, Clock clock) {
        this.maxAge = maxAge;
        this.clock = clock;
    }

    public boolean isStale(String occurredAt) {
        if (occurredAt == null || occurredAt.isBlank()) {
            return false;
        }
        try {
            return Instant.parse(occurredAt).isBefore(clock.instant().minus(maxAge));
        } catch (DateTimeParseException exp) {
            return false;
        }
    }
}
