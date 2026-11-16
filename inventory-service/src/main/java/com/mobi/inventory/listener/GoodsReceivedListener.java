package com.mobi.inventory.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobi.inventory.entity.AdjustmentReason;
import com.mobi.inventory.event.GoodsReceivedEvent;
import com.mobi.inventory.service.StockLedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puts counted goods on the shelf. For each line of a GOODS_RECEIVED event with good units,
 * one RECEIVED ledger entry referenced {@code PO_RECEIPT:<receiptId>}. The ledger applies a
 * reference at most once per product, so a redelivered event (the outbox publishes at least
 * once) or a replay after a crash halfway through the lines never adds stock twice.
 * Damaged units never entered stock and are recorded on the PO only.
 */
@Component
public class GoodsReceivedListener {

    private static final Logger log = LoggerFactory.getLogger(GoodsReceivedListener.class);
    public static final String REFERENCE_TYPE = "PO_RECEIPT";

    private final StockLedgerService ledger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoodsReceivedListener(StockLedgerService ledger) {
        this.ledger = ledger;
    }

    // One transaction for the whole receipt: every line's stock goes in, or none does and
    // the record is retried. A receipt is never left half-applied.
    @Transactional
    @KafkaListener(topics = "${procurement.kafka.topic}", containerFactory = "procurementEventListenerFactory")
    public void onProcurementEvent(String payload) {
        GoodsReceivedEvent event = parse(payload);
        if (!"GOODS_RECEIVED".equals(event.type())) {
            return;
        }
        if (event.receiptId() == null || event.lines() == null) {
            throw new IllegalArgumentException("GOODS_RECEIVED without receiptId or lines: " + event.eventId());
        }
        String actor = event.actor() == null ? "procurement" : event.actor();
        for (GoodsReceivedEvent.Line line : event.lines()) {
            if (line.productId() == null || line.quantityGood() == null || line.quantityGood() <= 0) {
                continue;
            }
            ledger.adjust(line.productId(), line.quantityGood(), AdjustmentReason.RECEIVED,
                    "Received against " + event.poNumber(), REFERENCE_TYPE, String.valueOf(event.receiptId()), actor);
        }
        log.info("Applied receipt {} for {}", event.receiptId(), event.poNumber());
    }

    private GoodsReceivedEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, GoodsReceivedEvent.class);
        } catch (JsonProcessingException exp) {
            throw new IllegalArgumentException("Unreadable procurement event: " + exp.getOriginalMessage(), exp);
        }
    }
}
