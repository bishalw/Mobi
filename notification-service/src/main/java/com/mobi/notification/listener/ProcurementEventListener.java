package com.mobi.notification.listener;

import com.mobi.notification.catalog.EventAge;
import com.mobi.notification.catalog.ProductNames;
import com.mobi.notification.event.ProcurementEvent;
import com.mobi.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Purchase-order email (procurement spec §9): the PO to the supplier's order desk when it
 * is sent, the supplier's answer to admins, and shipment notices to warehouse staff.
 * GOODS_RECEIVED is for inventory-service and sends nothing.
 */
@Component
public class ProcurementEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProcurementEventListener.class);

    private final NotificationDispatcher dispatcher;
    private final ProductNames productNames;
    private final EventAge eventAge;

    public ProcurementEventListener(NotificationDispatcher dispatcher, ProductNames productNames, EventAge eventAge) {
        this.dispatcher = dispatcher;
        this.productNames = productNames;
        this.eventAge = eventAge;
    }

    @KafkaListener(
            topics = "${procurement.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "procurementEventKafkaListenerContainerFactory"
    )
    public void handle(ProcurementEvent event) {
        if (event == null || event.type() == null) {
            log.warn("Received procurement event with missing type, skipping. payload={}", event);
            return;
        }
        if (eventAge.isStale(event.occurredAt())) {
            log.info("Skipping stale {} for {} from {}", event.type(), event.poNumber(), event.occurredAt());
            dispatcher.skip(event.eventId(), "stale-" + event.type(), "older than the staff alert window");
            return;
        }
        String supplier = event.supplierName() == null ? "The supplier" : event.supplierName();
        Map<String, Object> model = new HashMap<>();
        model.put("poNumber", event.poNumber());
        model.put("supplierName", supplier);
        model.put("note", event.note());
        model.put("lines", lines(event));

        switch (event.type()) {
            case "PO_SENT" -> dispatcher.emailAddress(event.eventId(), event.supplierEmail(), "po-sent",
                    "New purchase order " + event.poNumber() + " from Mobi", model);
            case "PO_ACCEPTED" -> dispatcher.emailRole(event.eventId(), "ADMIN", "po-accepted",
                    supplier + " accepted " + event.poNumber(), model);
            case "PO_REJECTED" -> dispatcher.emailRole(event.eventId(), "ADMIN", "po-rejected",
                    supplier + " rejected " + event.poNumber(), model);
            case "SHIPMENT_NOTICE_CREATED" -> dispatcher.emailRole(event.eventId(), "WAREHOUSE", "shipment-notice",
                    "Inbound: " + event.poNumber() + " from " + supplier, model);
            default -> log.debug("No email for {} on {}", event.type(), event.poNumber());
        }
    }

    // Maps, not the records themselves, so the templates read plain properties.
    private List<Map<String, Object>> lines(ProcurementEvent event) {
        if (event.lines() == null) {
            return List.of();
        }
        return event.lines().stream().map(line -> {
            Map<String, Object> row = new HashMap<>();
            row.put("productId", line.productId());
            row.put("name", productNames.nameOf(line.productId()));
            row.put("quantityOrdered", line.quantityOrdered());
            row.put("quantityShipped", line.quantityShipped());
            return row;
        }).toList();
    }
}
