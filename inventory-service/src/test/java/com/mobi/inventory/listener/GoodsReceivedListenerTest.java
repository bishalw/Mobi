package com.mobi.inventory.listener;

import com.mobi.inventory.entity.AdjustmentReason;
import com.mobi.inventory.service.StockLedgerService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class GoodsReceivedListenerTest {

    private final StockLedgerService ledger = mock(StockLedgerService.class);
    private final GoodsReceivedListener listener = new GoodsReceivedListener(ledger);

    @Test
    void eachLineWithGoodUnitsBecomesOneReferencedReceivedEntry() {
        listener.onProcurementEvent("""
                {"eventId":"e1","type":"GOODS_RECEIVED","occurredAt":"2026-11-12T10:00:00Z","poId":7,
                 "poNumber":"PO-000007","supplierId":1,"actor":"warehouse","receiptId":42,"newField":true,
                 "lines":[{"productId":3,"quantityGood":48,"quantityDamaged":2},
                          {"productId":4,"quantityGood":0,"quantityDamaged":5}]}
                """);

        verify(ledger).adjust(3L, 48, AdjustmentReason.RECEIVED, "Received against PO-000007", "PO_RECEIPT", "42", "warehouse");
        verify(ledger, never()).adjust(org.mockito.ArgumentMatchers.eq(4L), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void otherProcurementEventsAreIgnored() {
        listener.onProcurementEvent("{\"eventId\":\"e2\",\"type\":\"PO_SENT\",\"poId\":7,\"lines\":[{\"productId\":3,\"quantityOrdered\":50}]}");
        verifyNoInteractions(ledger);
    }

    @Test
    void unreadablePayloadGoesStraightToTheDeadLetterTopic() {
        // IllegalArgumentException is marked not retryable in KafkaConfig.
        assertThrows(IllegalArgumentException.class, () -> listener.onProcurementEvent("not json"));
        assertThrows(IllegalArgumentException.class,
                () -> listener.onProcurementEvent("{\"eventId\":\"e3\",\"type\":\"GOODS_RECEIVED\"}"));
        verify(ledger, never()).adjust(anyLong(), anyInt(), any(), any(), any(), any(), any());
    }
}
