package com.mobi.inventory.service;

import com.mobi.inventory.dto.StockAdjustmentResult;
import com.mobi.inventory.entity.AdjustmentReason;
import com.mobi.inventory.entity.InventoryItem;
import com.mobi.inventory.entity.StockAdjustment;
import com.mobi.inventory.exception.BadRequestException;
import com.mobi.inventory.exception.InsufficientInventoryException;
import com.mobi.inventory.exception.ResourceNotFoundException;
import com.mobi.inventory.repository.InventoryRepository;
import com.mobi.inventory.repository.StockAdjustmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mobi.inventory.event.LowStockAlerts;
import com.mobi.inventory.outbox.OutboxEvent;
import com.mobi.inventory.outbox.OutboxEventRepository;
import com.mobi.inventory.outbox.OutboxTracing;

class StockLedgerServiceTest {

    @Test
    void lowStockIsRaisedOnceWhenStockCrossesTheReorderLevel() {
        stock(1L, 12);
        inventory.findByProductIdInWithLock(List.of(1L)).get(0).setReorderLevel(10);

        service.adjustManually(1L, -1, AdjustmentReason.DAMAGED, "Cracked", "warehouse"); // 11: still above
        verify(outbox, never()).save(any(OutboxEvent.class));
        service.adjustManually(1L, -2, AdjustmentReason.DAMAGED, "Cracked", "warehouse"); // 9: crossed
        service.adjustManually(1L, -1, AdjustmentReason.LOST, "Missing", "warehouse");    // 8: already low
        verify(outbox, org.mockito.Mockito.times(1)).save(any(OutboxEvent.class));
    }


    private InventoryRepository inventory;
    private StockAdjustmentRepository ledger;
    private StockLedgerService service;
    private OutboxEventRepository outbox;
    private LowStockAlerts alerts;

    @BeforeEach
    void setUp() {
        outbox = mock(OutboxEventRepository.class);
        alerts = new LowStockAlerts(outbox, OutboxTracing.noop(), "inventory-events");
        inventory = mock(InventoryRepository.class);
        ledger = mock(StockAdjustmentRepository.class);
        service = new StockLedgerService(inventory, ledger, alerts);
        when(inventory.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledger.save(any(StockAdjustment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ledger.findByReferenceTypeAndReferenceIdAndProductId(any(), any(), any())).thenReturn(Optional.empty());
    }

    private void stock(long productId, int available) {
        InventoryItem item = new InventoryItem(productId, available, "A1");
        when(inventory.findByProductIdInWithLock(List.of(productId))).thenReturn(List.of(item));
        when(inventory.findByProductId(productId)).thenReturn(Optional.of(item));
    }

    @Test
    void writeOffReducesStockAndRecordsWhoAndWhy() {
        stock(1L, 10);

        StockAdjustmentResult result = service.adjustManually(1L, -2, AdjustmentReason.DAMAGED, "dropped", "warehouse");

        assertThat(result.item().getAvailableQuantity()).isEqualTo(8);
        assertThat(result.adjustment().availableAfter()).isEqualTo(8);
        assertThat(result.adjustment().createdBy()).isEqualTo("warehouse");
        assertThat(result.adjustment().note()).isEqualTo("dropped");
    }

    @Test
    void stockCannotGoBelowZero() {
        stock(1L, 1);

        assertThatThrownBy(() -> service.adjustManually(1L, -2, AdjustmentReason.LOST, "missing", "warehouse"))
                .isInstanceOf(InsufficientInventoryException.class);
        verify(ledger, never()).save(any());
    }

    @Test
    void firstStockCreatesTheRecord() {
        when(inventory.findByProductIdInWithLock(List.of(5L))).thenReturn(List.of());

        StockAdjustmentResult result = service.adjustManually(5L, 30, AdjustmentReason.INITIAL, null, "admin");

        ArgumentCaptor<InventoryItem> saved = ArgumentCaptor.forClass(InventoryItem.class);
        verify(inventory).save(saved.capture());
        assertThat(saved.getValue().getProductId()).isEqualTo(5L);
        assertThat(result.item().getAvailableQuantity()).isEqualTo(30);
    }

    @Test
    void removingStockThatWasNeverRecordedIsNotFound() {
        when(inventory.findByProductIdInWithLock(List.of(5L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.adjustManually(5L, -1, AdjustmentReason.COUNT_CORRECTION, "recount", "warehouse"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reasonDecidesTheSignAndWhetherANoteIsNeeded() {
        stock(1L, 10);

        assertThatThrownBy(() -> service.adjustManually(1L, 3, AdjustmentReason.DAMAGED, "x", "w")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.adjustManually(1L, -3, AdjustmentReason.INITIAL, null, "w")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.adjustManually(1L, 0, AdjustmentReason.COUNT_CORRECTION, "x", "w")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.adjustManually(1L, 2, AdjustmentReason.COUNT_CORRECTION, "  ", "w")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void receivedStockOnlyComesFromPurchaseOrders() {
        stock(1L, 10);

        assertThatThrownBy(() -> service.adjustManually(1L, 5, AdjustmentReason.RECEIVED, null, "warehouse"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void referencedAdjustmentAppliesOnlyOnce() {
        stock(1L, 10);
        StockAdjustment earlier = new StockAdjustment(1L, 48, AdjustmentReason.RECEIVED, null, "PO_RECEIPT", "42", "warehouse", 58);
        when(ledger.findByReferenceTypeAndReferenceIdAndProductId("PO_RECEIPT", "42", 1L)).thenReturn(Optional.of(earlier));

        StockAdjustmentResult result = service.adjust(1L, 48, AdjustmentReason.RECEIVED, null, "PO_RECEIPT", "42", "warehouse");

        assertThat(result.adjustment().availableAfter()).isEqualTo(58);
        verify(inventory, never()).save(any());
        verify(ledger, never()).save(any());
    }

    @Test
    void reorderLevelMarksLowStock() {
        stock(1L, 4);

        var response = service.updateDetails(1L, new com.mobi.inventory.dto.UpdateInventoryRequest(null, 5));

        assertThat(response.isLowStock()).isTrue();
        assertThat(response.getWarehouseLocation()).isEqualTo("A1");
    }
}
