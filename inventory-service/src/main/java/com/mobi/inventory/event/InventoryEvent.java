package com.mobi.inventory.event;

import java.time.Instant;

/** One record on {@code inventory-events}, keyed by product id. Only LOW_STOCK for now. */
/** {@code unsoldQuantity} (available + reserved) is what crossed the reorder level. */
public record InventoryEvent(String eventId, String type, Instant occurredAt, Long productId,
                             int availableQuantity, int unsoldQuantity, int reorderLevel, String warehouseLocation,
                             String cause) {
}
