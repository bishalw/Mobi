package com.mobi.notification.listener;

import com.mobi.notification.catalog.EventAge;
import com.mobi.notification.catalog.ProductNames;
import com.mobi.notification.event.InventoryEvent;
import com.mobi.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** LOW_STOCK goes to every admin, who decides whether to raise a purchase order. */
@Component
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private final NotificationDispatcher dispatcher;
    private final ProductNames productNames;
    private final EventAge eventAge;

    public InventoryEventListener(NotificationDispatcher dispatcher, ProductNames productNames, EventAge eventAge) {
        this.dispatcher = dispatcher;
        this.productNames = productNames;
        this.eventAge = eventAge;
    }

    @KafkaListener(
            topics = "${inventory.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "inventoryEventKafkaListenerContainerFactory"
    )
    public void handle(InventoryEvent event) {
        if (event == null || !"LOW_STOCK".equals(event.type())) {
            log.debug("Ignoring inventory event {}", event);
            return;
        }
        if (eventAge.isStale(event.occurredAt())) {
            log.info("Skipping stale LOW_STOCK for product {} from {}", event.productId(), event.occurredAt());
            dispatcher.skip(event.eventId(), "low-stock", "older than the staff alert window");
            return;
        }
        String name = productNames.nameOf(event.productId());
        Map<String, Object> model = new HashMap<>();
        model.put("productId", event.productId());
        model.put("productName", name);
        model.put("availableQuantity", event.remaining());
        model.put("reorderLevel", event.reorderLevel());
        model.put("warehouseLocation", event.warehouseLocation());
        dispatcher.emailRole(event.eventId(), "ADMIN", "low-stock",
                "Low stock: " + name + " is down to " + event.remaining(), model);
    }
}
