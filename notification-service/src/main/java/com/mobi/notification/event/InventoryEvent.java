package com.mobi.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Consumer-side copy of inventory-service's event. Only LOW_STOCK exists today. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryEvent(String eventId, String type, String occurredAt, Long productId,
                             Integer availableQuantity, Integer unsoldQuantity, Integer reorderLevel,
                             String warehouseLocation, String cause) {

    /** Stock left that isn't sold yet; older events without it fall back to available. */
    public Integer remaining() {
        return unsoldQuantity != null ? unsoldQuantity : availableQuantity;
    }
}
