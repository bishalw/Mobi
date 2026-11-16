package com.mobi.procurement.service;

import com.mobi.procurement.dto.AcceptPurchaseOrderRequest;
import com.mobi.procurement.dto.PurchaseOrderRequest;
import com.mobi.procurement.dto.PurchaseOrderResponse;
import com.mobi.procurement.dto.ReceiptRequest;
import com.mobi.procurement.dto.ShipmentNoticeRequest;
import com.mobi.procurement.dto.StatusNoteRequest;
import com.mobi.procurement.entity.PurchaseOrder;
import com.mobi.procurement.entity.PurchaseOrderLine;
import com.mobi.procurement.entity.PurchaseOrderStatus;
import com.mobi.procurement.entity.Receipt;
import com.mobi.procurement.entity.ShipmentNotice;
import com.mobi.procurement.entity.Supplier;
import com.mobi.procurement.entity.SupplierProduct;
import com.mobi.procurement.event.ProcurementEvent;
import com.mobi.procurement.event.ProcurementEventPublisher;
import com.mobi.procurement.event.ProcurementEventType;
import com.mobi.procurement.exception.BadRequestException;
import com.mobi.procurement.exception.ConflictException;
import com.mobi.procurement.exception.NotFoundException;
import com.mobi.procurement.repository.PurchaseOrderRepository;
import com.mobi.procurement.repository.ReceiptRepository;
import com.mobi.procurement.repository.ShipmentNoticeRepository;
import com.mobi.procurement.repository.SupplierProductRepository;
import com.mobi.procurement.repository.SupplierRepository;
import com.mobi.procurement.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOrderServiceTest {

    private static final CurrentUser ADMIN = new CurrentUser(1L, "admin", "ADMIN", null);
    private static final CurrentUser WAREHOUSE = new CurrentUser(2L, "warehouse", "WAREHOUSE", null);
    private static final CurrentUser SUPPLIER = new CurrentUser(3L, "supplier", "SUPPLIER", 1L);
    private static final CurrentUser OTHER_SUPPLIER = new CurrentUser(4L, "other", "SUPPLIER", 2L);

    private PurchaseOrderRepository purchaseOrders;
    private SupplierRepository suppliers;
    private SupplierProductRepository catalogue;
    private ShipmentNoticeRepository notices;
    private ReceiptRepository receipts;
    private ProcurementEventPublisher events;
    private PurchaseOrderService service;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        purchaseOrders = mock(PurchaseOrderRepository.class);
        suppliers = mock(SupplierRepository.class);
        catalogue = mock(SupplierProductRepository.class);
        notices = mock(ShipmentNoticeRepository.class);
        receipts = mock(ReceiptRepository.class);
        when(receipts.findByReceiptKey(any())).thenReturn(Optional.empty());
        when(receipts.saveAndFlush(any())).thenAnswer(inv -> {
            Receipt receipt = inv.getArgument(0);
            TestIds.set(receipt, "id", 42L);
            return receipt;
        });
        events = mock(ProcurementEventPublisher.class);
        service = new PurchaseOrderService(purchaseOrders, suppliers, catalogue, notices, receipts, events, new PurchaseOrderViews());

        supplier = new Supplier("Mobi Distribution", "orders@mobi-distribution.test");
        TestIds.set(supplier, "id", 1L);
        when(suppliers.findById(1L)).thenReturn(Optional.of(supplier));
        when(catalogue.findBySupplierIdOrderByProductIdAsc(1L)).thenReturn(List.of(
                new SupplierProduct(1L, 3L, "MD-3", new BigDecimal("600.00")),
                new SupplierProduct(1L, 4L, null, new BigDecimal("300.00"))));
        when(notices.findByPoIdOrderByCreatedAtAscIdAsc(any())).thenReturn(List.of());
        when(purchaseOrders.saveAndFlush(any())).thenAnswer(inv -> {
            PurchaseOrder po = inv.getArgument(0);
            if (po.getId() == null) {
                TestIds.set(po, "id", 7L);
            }
            long lineId = 700;
            for (PurchaseOrderLine line : po.getLines()) {
                if (line.getId() == null) {
                    TestIds.set(line, "id", ++lineId);
                }
            }
            return po;
        });
    }

    @Test
    void createDraftTakesUnitCostFromCatalogueUnlessGiven() {
        PurchaseOrderResponse po = service.create(ADMIN, new PurchaseOrderRequest(1L, LocalDate.now().plusDays(7), " ",
                List.of(new PurchaseOrderRequest.Line(3L, 50, null),
                        new PurchaseOrderRequest.Line(4L, 10, new BigDecimal("280.00")))));

        assertEquals("PO-000007", po.poNumber());
        assertEquals(PurchaseOrderStatus.DRAFT, po.status());
        assertEquals(new BigDecimal("600.00"), po.lines().get(0).unitCost());
        assertEquals("MD-3", po.lines().get(0).supplierSku());
        assertEquals(new BigDecimal("280.00"), po.lines().get(1).unitCost());
        assertEquals(new BigDecimal("32800.00"), po.total());
        assertEquals("admin", po.createdBy());
        assertNull(po.notes());
        assertEquals(Set.of(PurchaseOrderAction.EDIT, PurchaseOrderAction.SEND, PurchaseOrderAction.CANCEL), po.actions());
    }

    @Test
    void productOutsideTheSupplierCatalogueIsRejected() {
        BadRequestException exp = assertThrows(BadRequestException.class, () -> service.create(ADMIN,
                new PurchaseOrderRequest(1L, null, null, List.of(new PurchaseOrderRequest.Line(99L, 1, null)))));
        assertEquals("Product 99 is not in Mobi Distribution's catalogue", exp.getMessage());
    }

    @Test
    void duplicateProductAndInactiveSupplierAreRejected() {
        assertThrows(BadRequestException.class, () -> service.create(ADMIN, new PurchaseOrderRequest(1L, null, null,
                List.of(new PurchaseOrderRequest.Line(3L, 1, null), new PurchaseOrderRequest.Line(3L, 2, null)))));
        supplier.update(supplier.getName(), supplier.getContactEmail(), false);
        assertThrows(BadRequestException.class, () -> service.create(ADMIN,
                new PurchaseOrderRequest(1L, null, null, List.of(new PurchaseOrderRequest.Line(3L, 1, null)))));
    }

    @Test
    void sendPublishesPoSent() {
        PurchaseOrder po = stored(PurchaseOrderStatus.DRAFT);
        PurchaseOrderResponse sent = service.send(ADMIN, 7L);
        assertEquals(PurchaseOrderStatus.SENT, sent.status());
        verify(events).publish(ProcurementEventType.PO_SENT, po, supplier, "admin", null);
    }

    @Test
    void onlyDraftsCanBeEdited() {
        stored(PurchaseOrderStatus.SENT);
        assertThrows(ConflictException.class, () -> service.update(ADMIN, 7L,
                new PurchaseOrderRequest(1L, null, null, List.of(new PurchaseOrderRequest.Line(3L, 1, null)))));
    }

    @Test
    void supplierCannotSeeAnotherSuppliersPoOrADraft() {
        stored(PurchaseOrderStatus.SENT);
        assertThrows(NotFoundException.class, () -> service.get(OTHER_SUPPLIER, 7L));
        assertEquals("PO-000007", service.get(SUPPLIER, 7L).poNumber());

        stored(PurchaseOrderStatus.DRAFT);
        assertThrows(NotFoundException.class, () -> service.get(SUPPLIER, 7L));
    }

    @Test
    void draftCancelledBeforeSendingStaysInvisibleToTheSupplier() {
        PurchaseOrder po = stored(PurchaseOrderStatus.CANCELLED);
        TestIds.set(po, "sentAt", null);
        assertThrows(NotFoundException.class, () -> service.get(SUPPLIER, 7L));
        assertEquals("PO-000007", service.get(ADMIN, 7L).poNumber());
    }

    @Test
    void replayedKeyWithDifferentCountsIsRefused() {
        stored(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        when(receipts.findByReceiptKey("key-6")).thenReturn(Optional.of(new Receipt("key-6", 7L, "warehouse", null,
                PurchaseOrderService.fingerprint(new ReceiptRequest(null, List.of(new ReceiptRequest.Line(701L, 10, 0)))))));
        assertThrows(ConflictException.class, () -> service.receive(WAREHOUSE, 7L, "key-6",
                new ReceiptRequest(null, List.of(new ReceiptRequest.Line(701L, 12, 0)))));
    }

    @Test
    void supplierListIsForcedToOwnPosWithoutDrafts() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<PurchaseOrderStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        when(purchaseOrders.search(anyCollection(), any(), anyBoolean(), any(Pageable.class))).thenReturn(Page.empty());

        service.list(SUPPLIER, null, 2L, 0, 20);

        verify(purchaseOrders).search(statuses.capture(), eq(1L), eq(true), any(Pageable.class));
        assertFalse(statuses.getValue().contains(PurchaseOrderStatus.DRAFT));
        assertEquals(PurchaseOrderStatus.values().length - 1, statuses.getValue().size());
    }

    @Test
    void supplierWithoutALinkedSupplierSeesNothing() {
        CurrentUser unlinked = new CurrentUser(5L, "x", "SUPPLIER", null);
        assertEquals(0, service.list(unlinked, null, null, 0, 20).totalElements());
        verify(purchaseOrders, never()).search(anyCollection(), any(), anyBoolean(), any(Pageable.class));
    }

    @Test
    void acceptSetsExpectedDateAndPublishes() {
        PurchaseOrder po = stored(PurchaseOrderStatus.SENT);
        LocalDate date = LocalDate.now().plusDays(5);
        PurchaseOrderResponse accepted = service.accept(SUPPLIER, 7L, new AcceptPurchaseOrderRequest(date));
        assertEquals(PurchaseOrderStatus.ACCEPTED, accepted.status());
        assertEquals(date, accepted.expectedDate());
        assertEquals(Set.of(PurchaseOrderAction.SHIP), accepted.actions());
        verify(events).publish(ProcurementEventType.PO_ACCEPTED, po, supplier, "supplier", null);
    }

    @Test
    void acceptAllowsTheSuppliersTodayButNotThePast() {
        stored(PurchaseOrderStatus.SENT);
        LocalDate utcToday = LocalDate.now(java.time.ZoneOffset.UTC);
        assertThrows(BadRequestException.class,
                () -> service.accept(SUPPLIER, 7L, new AcceptPurchaseOrderRequest(utcToday.minusDays(2))));
        // West of UTC in the evening, "today" is yesterday in UTC: still accepted.
        assertEquals(PurchaseOrderStatus.ACCEPTED,
                service.accept(SUPPLIER, 7L, new AcceptPurchaseOrderRequest(utcToday.minusDays(1))).status());
    }

    @Test
    void rejectNeedsAReason() {
        PurchaseOrder po = stored(PurchaseOrderStatus.SENT);
        assertThrows(BadRequestException.class, () -> service.reject(SUPPLIER, 7L, new StatusNoteRequest("  ")));
        PurchaseOrderResponse rejected = service.reject(SUPPLIER, 7L, new StatusNoteRequest("Discontinued"));
        assertEquals(PurchaseOrderStatus.REJECTED, rejected.status());
        assertEquals("Discontinued", rejected.statusNote());
        verify(events).publish(ProcurementEventType.PO_REJECTED, po, supplier, "supplier", "Discontinued");
    }

    @Test
    void shipmentNoticeUpdatesShippedTotalsAndStatus() {
        PurchaseOrder po = stored(PurchaseOrderStatus.ACCEPTED);
        PurchaseOrderResponse shipped = service.ship(SUPPLIER, 7L, new ShipmentNoticeRequest("DHL", "TRK1",
                LocalDate.now().plusDays(2), List.of(new ShipmentNoticeRequest.Line(701L, 30))));

        assertEquals(PurchaseOrderStatus.SHIPPED, shipped.status());
        assertEquals(30, po.getLines().get(0).getQuantityShipped());
        verify(notices).save(any(ShipmentNotice.class));
        verify(events).publish(eq(ProcurementEventType.SHIPMENT_NOTICE_CREATED), eq(po), eq(supplier), eq("supplier"), isNull(),
                any(), isNull(), anyList());
    }

    @Test
    void shipmentIsCappedByWhatIsStillOutstanding() {
        PurchaseOrder po = stored(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        po.getLines().get(0).recordReceived(40, 0);
        BadRequestException exp = assertThrows(BadRequestException.class, () -> service.ship(SUPPLIER, 7L,
                new ShipmentNoticeRequest("DHL", null, null, List.of(new ShipmentNoticeRequest.Line(701L, 11)))));
        assertEquals("Product 3: 11 shipped would exceed the 10 still outstanding (ordered 50)", exp.getMessage());
        // Replacing the short 10 after a receipt is allowed, even though 50 were already announced.
        po.getLines().get(0).recordShipped(50);
        service.ship(SUPPLIER, 7L, new ShipmentNoticeRequest("DHL", null, null, List.of(new ShipmentNoticeRequest.Line(701L, 10))));
        assertThrows(BadRequestException.class, () -> service.ship(SUPPLIER, 7L,
                new ShipmentNoticeRequest("DHL", null, null, List.of(new ShipmentNoticeRequest.Line(999L, 1)))));
    }

    @Test
    void closeShortNeedsANote() {
        stored(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        assertThrows(BadRequestException.class, () -> service.close(ADMIN, 7L, null));
        assertEquals(PurchaseOrderStatus.RECEIVED, service.close(ADMIN, 7L, new StatusNoteRequest("Rest backordered")).status());
    }

    @Test
    void warehouseSeesNoCostsAndSupplierSeesNoInternalFields() {
        stored(PurchaseOrderStatus.SHIPPED);

        PurchaseOrderResponse forWarehouse = service.get(WAREHOUSE, 7L);
        assertNull(forWarehouse.total());
        assertNull(forWarehouse.lines().get(0).unitCost());
        assertNull(forWarehouse.lines().get(0).lineTotal());
        assertEquals("admin", forWarehouse.createdBy());
        assertEquals(Set.of(PurchaseOrderAction.RECEIVE), forWarehouse.actions());

        PurchaseOrderResponse forSupplier = service.get(SUPPLIER, 7L);
        assertNull(forSupplier.createdBy());
        assertEquals(new BigDecimal("600.00"), forSupplier.lines().get(0).unitCost());
    }

    @Test
    void statusFilterIsPassedThrough() {
        PurchaseOrder po = stored(PurchaseOrderStatus.SENT);
        when(purchaseOrders.search(anyCollection(), any(), anyBoolean(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(po)));
        var page = service.list(WAREHOUSE, EnumSet.of(PurchaseOrderStatus.SENT), null, 0, 500);
        assertEquals(1, page.content().size());
        assertNull(page.content().get(0).total());
        assertEquals("Mobi Distribution", page.content().get(0).supplierName());
    }

    // --- Receiving -------------------------------------------------------------------

    @Test
    void receiptUpdatesTotalsStatusAndPublishesGoodsReceived() {
        PurchaseOrder po = stored(PurchaseOrderStatus.SHIPPED);
        PurchaseOrderResponse after = service.receive(WAREHOUSE, 7L, "key-1",
                new ReceiptRequest("Two boxes crushed", List.of(new ReceiptRequest.Line(701L, 48, 2))));

        assertEquals(PurchaseOrderStatus.RECEIVED, after.status());
        assertEquals(48, po.getLines().get(0).getQuantityReceived());
        assertEquals(2, po.getLines().get(0).getQuantityDamaged());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProcurementEvent.Line>> lines = ArgumentCaptor.forClass(List.class);
        verify(events).publish(eq(ProcurementEventType.GOODS_RECEIVED), eq(po), eq(supplier), eq("warehouse"),
                eq("Two boxes crushed"), isNull(), eq(42L), lines.capture());
        assertEquals(new ProcurementEvent.Line(3L, null, null, 48, 2), lines.getValue().get(0));
    }

    @Test
    void shortReceiptLeavesThePoPartiallyReceived() {
        stored(PurchaseOrderStatus.ACCEPTED);
        PurchaseOrderResponse after = service.receive(ADMIN, 7L, "key-2",
                new ReceiptRequest(null, List.of(new ReceiptRequest.Line(701L, 30, 0))));
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, after.status());
        assertEquals(20, after.lines().get(0).outstanding());
        assertEquals(Set.of(PurchaseOrderAction.RECEIVE, PurchaseOrderAction.CLOSE), after.actions());
    }

    @Test
    void cannotCountMoreThanIsOutstanding() {
        PurchaseOrder po = stored(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        po.getLines().get(0).recordReceived(45, 0);
        ConflictException exp = assertThrows(ConflictException.class, () -> service.receive(WAREHOUSE, 7L, "key-3",
                new ReceiptRequest(null, List.of(new ReceiptRequest.Line(701L, 5, 1)))));
        assertEquals("Product 3: counting 6 would exceed the 5 still outstanding (ordered 50)", exp.getMessage());
        verify(events, never()).publish(any(), any(), any(), any(), any(), any(), any(), anyList());
    }

    @Test
    void replayedIdempotencyKeyReturnsTheResultWithoutCountingAgain() {
        PurchaseOrder po = stored(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        Receipt earlier = new Receipt("key-4", 7L, "warehouse", null, PurchaseOrderService.fingerprint(new ReceiptRequest(null, List.of(new ReceiptRequest.Line(701L, 10, 0)))));
        when(receipts.findByReceiptKey("key-4")).thenReturn(Optional.of(earlier));

        service.receive(WAREHOUSE, 7L, "key-4", new ReceiptRequest(null, List.of(new ReceiptRequest.Line(701L, 10, 0))));

        assertEquals(0, po.getLines().get(0).getQuantityReceived());
        verify(receipts, never()).saveAndFlush(any());
        verify(events, never()).publish(any(), any(), any(), any(), any(), any(), any(), anyList());

        when(receipts.findByReceiptKey("key-5")).thenReturn(Optional.of(new Receipt("key-5", 99L, "warehouse", null, null)));
        assertThrows(ConflictException.class, () -> service.receive(WAREHOUSE, 7L, "key-5",
                new ReceiptRequest(null, List.of(new ReceiptRequest.Line(701L, 1, 0)))));
    }

    @Test
    void receivingNeedsAKeyAReceivableStatusAndSomethingCounted() {
        stored(PurchaseOrderStatus.SHIPPED);
        ReceiptRequest one = new ReceiptRequest(null, List.of(new ReceiptRequest.Line(701L, 1, 0)));
        assertThrows(BadRequestException.class, () -> service.receive(WAREHOUSE, 7L, " ", one));
        assertThrows(BadRequestException.class, () -> service.receive(WAREHOUSE, 7L, "k",
                new ReceiptRequest(null, List.of(new ReceiptRequest.Line(701L, 0, 0)))));
        stored(PurchaseOrderStatus.SENT);
        assertThrows(ConflictException.class, () -> service.receive(WAREHOUSE, 7L, "k2", one));
    }

    private PurchaseOrder stored(PurchaseOrderStatus status) {
        PurchaseOrder po = PurchaseOrderStateMachineTest.po(status);
        if (status != PurchaseOrderStatus.DRAFT) {
            TestIds.set(po, "sentAt", java.time.LocalDateTime.now());
        }
        PurchaseOrderLine line = new PurchaseOrderLine(3L, 50, new BigDecimal("600.00"));
        TestIds.set(line, "id", 701L);
        po.addLine(line);
        when(purchaseOrders.findById(7L)).thenReturn(Optional.of(po));
        when(suppliers.findAllById(any())).thenReturn(List.of(supplier));
        return po;
    }
}
