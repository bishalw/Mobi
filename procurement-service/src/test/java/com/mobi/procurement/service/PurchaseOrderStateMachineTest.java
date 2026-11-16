package com.mobi.procurement.service;

import com.mobi.procurement.entity.PurchaseOrder;
import com.mobi.procurement.entity.PurchaseOrderLine;
import com.mobi.procurement.entity.PurchaseOrderStatus;
import com.mobi.procurement.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.mobi.procurement.entity.PurchaseOrderStatus.ACCEPTED;
import static com.mobi.procurement.entity.PurchaseOrderStatus.CANCELLED;
import static com.mobi.procurement.entity.PurchaseOrderStatus.DRAFT;
import static com.mobi.procurement.entity.PurchaseOrderStatus.PARTIALLY_RECEIVED;
import static com.mobi.procurement.entity.PurchaseOrderStatus.RECEIVED;
import static com.mobi.procurement.entity.PurchaseOrderStatus.REJECTED;
import static com.mobi.procurement.entity.PurchaseOrderStatus.SENT;
import static com.mobi.procurement.entity.PurchaseOrderStatus.SHIPPED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseOrderStateMachineTest {

    /** Spec §6.2, written out as a table. Anything not listed must be refused. */
    private static final Map<PurchaseOrderAction, Set<PurchaseOrderStatus>> EXPECTED = Map.of(
            PurchaseOrderAction.EDIT, EnumSet.of(DRAFT),
            PurchaseOrderAction.SEND, EnumSet.of(DRAFT),
            PurchaseOrderAction.CANCEL, EnumSet.of(DRAFT, SENT, ACCEPTED, SHIPPED),
            PurchaseOrderAction.ACCEPT, EnumSet.of(SENT),
            PurchaseOrderAction.REJECT, EnumSet.of(SENT),
            PurchaseOrderAction.SHIP, EnumSet.of(ACCEPTED, SHIPPED, PARTIALLY_RECEIVED),
            PurchaseOrderAction.RECEIVE, EnumSet.of(ACCEPTED, SHIPPED, PARTIALLY_RECEIVED),
            PurchaseOrderAction.CLOSE, EnumSet.of(PARTIALLY_RECEIVED));

    @ParameterizedTest
    @EnumSource(PurchaseOrderAction.class)
    void allowsExactlyTheSpecifiedTransitions(PurchaseOrderAction action) {
        for (PurchaseOrderStatus status : PurchaseOrderStatus.values()) {
            assertEquals(EXPECTED.get(action).contains(status), PurchaseOrderStateMachine.allows(status, action),
                    action + " from " + status);
        }
    }

    @Test
    void placeholderPoNumberFitsTheColumn() {
        // po_number is VARCHAR(40); MySQL strict mode rejects a longer insert outright.
        assertTrue(new PurchaseOrder(1L, null, null, "admin").getPoNumber().length() <= 40);
    }

    @Test
    void refusedTransitionIsAConflictNamingThePo() {
        PurchaseOrder po = po(RECEIVED);
        ConflictException exp = assertThrows(ConflictException.class,
                () -> PurchaseOrderStateMachine.apply(po, PurchaseOrderAction.CANCEL, null));
        assertEquals("Cannot cancel purchase order PO-000007 while it is RECEIVED", exp.getMessage());
    }

    @Test
    void noCancelOnceGoodsAreIn() {
        assertThrows(ConflictException.class,
                () -> PurchaseOrderStateMachine.apply(po(PARTIALLY_RECEIVED), PurchaseOrderAction.CANCEL, null));
    }

    @Test
    void straightThroughHappyPath() {
        PurchaseOrder po = po(DRAFT);
        PurchaseOrderStateMachine.apply(po, PurchaseOrderAction.SEND, null);
        assertEquals(SENT, po.getStatus());
        assertNotNull(po.getSentAt());
        PurchaseOrderStateMachine.apply(po, PurchaseOrderAction.ACCEPT, null);
        assertEquals(ACCEPTED, po.getStatus());
        PurchaseOrderStateMachine.apply(po, PurchaseOrderAction.SHIP, null);
        assertEquals(SHIPPED, po.getStatus());
        // A second notice keeps the PO shipped.
        PurchaseOrderStateMachine.apply(po, PurchaseOrderAction.SHIP, null);
        assertEquals(SHIPPED, po.getStatus());
    }

    @Test
    void shipmentAfterAShortReceiptStaysPartiallyReceived() {
        PurchaseOrder po = po(PARTIALLY_RECEIVED);
        PurchaseOrderStateMachine.apply(po, PurchaseOrderAction.SHIP, null);
        assertEquals(PARTIALLY_RECEIVED, po.getStatus());
    }

    @Test
    void receiptIsCompleteOnlyWhenEveryLineIsAccountedFor() {
        PurchaseOrder po = po(SHIPPED);
        PurchaseOrderLine a = line(po, 1L, 50);
        PurchaseOrderLine b = line(po, 2L, 10);

        a.recordReceived(48, 2);
        PurchaseOrderStateMachine.apply(po, PurchaseOrderAction.RECEIVE, null);
        assertEquals(PARTIALLY_RECEIVED, po.getStatus());

        b.recordReceived(10, 0);
        PurchaseOrderStateMachine.apply(po, PurchaseOrderAction.RECEIVE, null);
        assertEquals(RECEIVED, po.getStatus());
    }

    @Test
    void closeShortRecordsTheNote() {
        PurchaseOrder po = po(PARTIALLY_RECEIVED);
        PurchaseOrderStateMachine.apply(po, PurchaseOrderAction.CLOSE, "Supplier out of stock");
        assertEquals(RECEIVED, po.getStatus());
        assertEquals("Supplier out of stock", po.getStatusNote());
        assertNotNull(po.getClosedAt());
    }

    @Test
    void rejectAndCancelAreTerminal() {
        for (PurchaseOrderStatus terminal : EnumSet.of(REJECTED, CANCELLED, RECEIVED)) {
            assertEquals(Set.of(), PurchaseOrderStateMachine.available(terminal), terminal.name());
        }
    }

    static PurchaseOrder po(PurchaseOrderStatus status) {
        PurchaseOrder po = new PurchaseOrder(1L, null, null, "admin");
        TestIds.set(po, "id", 7L);
        po.assignPoNumber();
        TestIds.set(po, "status", status);
        return po;
    }

    static PurchaseOrderLine line(PurchaseOrder po, Long productId, int ordered) {
        PurchaseOrderLine line = new PurchaseOrderLine(productId, ordered, new BigDecimal("10.00"));
        TestIds.set(line, "id", productId * 100);
        po.addLine(line);
        return line;
    }
}
