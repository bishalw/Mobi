package com.mobi.notification.catalog;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductNamesAndEventAgeTest {

    @Test
    void namesAreCachedAndAnOutageIsNotRetriedForAMinute() {
        AtomicInteger calls = new AtomicInteger();
        long[] now = {0};
        ProductNames names = new ProductNames(id -> {
            calls.incrementAndGet();
            if (id == 9L) {
                throw new IllegalStateException("product-service down");
            }
            return "Pixel 8";
        }, () -> now[0]);

        assertEquals("Pixel 8", names.nameOf(3L));
        assertEquals("Pixel 8", names.nameOf(3L));
        assertEquals(1, calls.get());

        assertEquals("Product #9", names.nameOf(9L));
        assertEquals("Product #9", names.nameOf(9L));      // within the backoff: no call
        assertEquals("Product #4", names.nameOf(4L));      // nor for other products
        assertEquals(2, calls.get());
        now[0] = 61_000;
        assertEquals("Pixel 8", names.nameOf(4L));         // tried again after a minute
        assertEquals(3, calls.get());
    }

    @Test
    void eventsOlderThanTheLimitAreStale() {
        EventAge age = new EventAge(Duration.ofHours(24), Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC));
        assertTrue(age.isStale("2026-11-10T12:00:00Z"));
        assertFalse(age.isStale("2026-11-12T11:00:00Z"));
        assertFalse(age.isStale(null));
        assertFalse(age.isStale("not a date"));
    }
}
