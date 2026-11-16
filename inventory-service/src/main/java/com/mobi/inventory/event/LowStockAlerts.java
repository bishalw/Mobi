package com.mobi.inventory.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mobi.inventory.entity.InventoryItem;
import com.mobi.inventory.outbox.OutboxEvent;
import com.mobi.inventory.outbox.OutboxEventRepository;
import com.mobi.inventory.outbox.OutboxTracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Raises LOW_STOCK when a change takes a product's <em>unsold</em> stock (available +
 * reserved) from above its reorder level to at or below it. Unsold stock only moves on a
 * ledger adjustment or a confirmed sale; reserving or releasing a cart moves units between
 * available and reserved without changing it. So each real drop is measured once, however
 * many reservations are open, and abandoned carts never alert. Crossing, not being low: a
 * product that stays low doesn't alert again until it recovers.
 * Written to the outbox in the caller's transaction.
 */
@Component
public class LowStockAlerts {

    private static final Logger log = LoggerFactory.getLogger(LowStockAlerts.class);

    private final OutboxEventRepository outbox;
    private final OutboxTracing tracing;
    private final String topic;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public LowStockAlerts(OutboxEventRepository outbox, OutboxTracing tracing,
                          @Value("${inventory.kafka.topic:inventory-events}") String topic) {
        this.outbox = outbox;
        this.tracing = tracing;
        this.topic = topic;
    }

    public static boolean crossed(int before, int after, int reorderLevel) {
        return reorderLevel > 0 && before > reorderLevel && after <= reorderLevel;
    }

    public static int unsold(InventoryItem item) {
        return item.getAvailableQuantity() + item.getReservedQuantity();
    }

    /** {@code unsoldBefore} is {@link #unsold} before this change; the item holds the state after it. */
    public void afterChange(InventoryItem item, int unsoldBefore, String cause) {
        if (!crossed(unsoldBefore, unsold(item), item.getReorderLevel())) {
            return;
        }
        InventoryEvent event = new InventoryEvent(UUID.randomUUID().toString(), "LOW_STOCK", Instant.now(),
                item.getProductId(), item.getAvailableQuantity(), unsold(item), item.getReorderLevel(),
                item.getWarehouseLocation(), cause);
        try {
            OutboxEvent row = new OutboxEvent(topic, String.valueOf(item.getProductId()), String.valueOf(item.getProductId()),
                    event.type(), objectMapper.writeValueAsString(event));
            row.setTraceparent(tracing.currentTraceparent());
            outbox.save(row);
            log.info("Product {} fell to {} (reorder level {}): LOW_STOCK", item.getProductId(),
                    item.getAvailableQuantity(), item.getReorderLevel());
        } catch (JsonProcessingException exp) {
            throw new IllegalStateException("Unable to serialize inventory event", exp);
        }
    }
}
