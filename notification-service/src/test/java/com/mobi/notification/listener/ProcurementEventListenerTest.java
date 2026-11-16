package com.mobi.notification.listener;

import com.mobi.notification.catalog.EventAge;
import com.mobi.notification.catalog.ProductNames;
import com.mobi.notification.event.InventoryEvent;
import com.mobi.notification.event.ProcurementEvent;
import com.mobi.notification.service.NotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProcurementEventListenerTest {

    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
    private final ProductNames names = new ProductNames(id -> "Pixel " + id);
    private final EventAge age = new EventAge(Duration.ofHours(24), Clock.fixed(NOW, ZoneOffset.UTC));
    private final ProcurementEventListener procurement = new ProcurementEventListener(dispatcher, names, age);
    private final InventoryEventListener inventory = new InventoryEventListener(dispatcher, names, age);

    private static ProcurementEvent event(String type, String occurredAt) {
        return new ProcurementEvent("e1", type, occurredAt, 7L, "PO-000007", 1L, "Mobi Distribution",
                "orders@mobi-distribution.test", "SENT", "admin", null,
                List.of(new ProcurementEvent.Line(3L, 50, null, null, null)));
    }

    @Test
    void sentPoGoesToTheSupplierWithProductNames() {
        procurement.handle(event("PO_SENT", NOW.toString()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> model = ArgumentCaptor.forClass(Map.class);
        verify(dispatcher).emailAddress(eq("e1"), eq("orders@mobi-distribution.test"), eq("po-sent"),
                eq("New purchase order PO-000007 from Mobi"), model.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) model.getValue().get("lines");
        assertEquals("Pixel 3", lines.get(0).get("name"));
    }

    @Test
    void answersGoToAdminsAndShipmentsToTheWarehouse() {
        procurement.handle(event("PO_REJECTED", NOW.toString()));
        procurement.handle(event("SHIPMENT_NOTICE_CREATED", NOW.toString()));
        verify(dispatcher).emailRole(eq("e1"), eq("ADMIN"), eq("po-rejected"), eq("Mobi Distribution rejected PO-000007"), anyMap());
        verify(dispatcher).emailRole(eq("e1"), eq("WAREHOUSE"), eq("shipment-notice"), eq("Inbound: PO-000007 from Mobi Distribution"), anyMap());
    }

    @Test
    void staleEventsSendNothingButAreRecorded() {
        procurement.handle(event("PO_SENT", "2026-11-01T00:00:00Z"));
        inventory.handle(new InventoryEvent("e2", "LOW_STOCK", "2026-11-01T00:00:00Z", 3L, 4, 9, 10, "C2", "SALE"));
        verify(dispatcher).skip("e1", "stale-PO_SENT", "older than the staff alert window");
        verify(dispatcher).skip("e2", "low-stock", "older than the staff alert window");
        org.mockito.Mockito.verifyNoMoreInteractions(dispatcher);
    }

    @Test
    void lowStockNamesTheProduct() {
        // Available 4, but 5 more are in paid-for carts not yet confirmed: 9 unsold is what crossed 10.
        inventory.handle(new InventoryEvent("e3", "LOW_STOCK", NOW.toString(), 3L, 4, 9, 10, "C2", "SALE"));
        verify(dispatcher).emailRole(eq("e3"), eq("ADMIN"), eq("low-stock"), eq("Low stock: Pixel 3 is down to 9"), anyMap());
    }
}
