package com.mobi.inventory.service;

import com.mobi.inventory.entity.InventoryItem;
import com.mobi.inventory.entity.Reservation;
import com.mobi.inventory.entity.ReservationItem;
import com.mobi.inventory.entity.ReservationStatus;
import com.mobi.inventory.exception.IllegalReservationStateException;
import com.mobi.inventory.repository.InventoryRepository;
import com.mobi.inventory.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.mobi.inventory.event.LowStockAlerts;
import com.mobi.inventory.outbox.OutboxEvent;
import com.mobi.inventory.outbox.OutboxEventRepository;
import com.mobi.inventory.outbox.OutboxTracing;

class InventoryServiceImplTest {

    private InventoryRepository inventoryRepository;
    private ReservationRepository reservationRepository;
    private InventoryServiceImpl service;
    private OutboxEventRepository outbox;
    private LowStockAlerts alerts;

    @BeforeEach
    void setUp() {
        outbox = mock(OutboxEventRepository.class);
        alerts = new LowStockAlerts(outbox, OutboxTracing.noop(), "inventory-events");
        inventoryRepository = mock(InventoryRepository.class);
        reservationRepository = mock(ReservationRepository.class);
        service = new InventoryServiceImpl(inventoryRepository, reservationRepository, alerts);
    }

    @Test
    void confirmedSaleThatCrossesTheReorderLevelRaisesLowStock() {
        Reservation reservation = activeReservation("order-9", 10L, 3);
        InventoryItem item = new InventoryItem(10L, 8, "A1"); // 11 before the reservation took 3
        item.setReservedQuantity(3);
        item.setReorderLevel(10);
        when(reservationRepository.findByReservationKey("order-9")).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByProductIdInWithLock(List.of(10L))).thenReturn(List.of(item));

        service.confirmReservation("order-9");

        verify(outbox).save(any(OutboxEvent.class));
    }

    @Test
    void lowStockFiresOnceAcrossOverlappingReservations() {
        // 12 on hand, reorder at 10. Two carts reserve 3 each (available 6, reserved 6),
        // then both pay. Unsold stock goes 12 -> 9 -> 6: one alert, on the first sale.
        InventoryItem item = new InventoryItem(10L, 6, "A1");
        item.setReservedQuantity(6);
        item.setReorderLevel(10);
        Reservation a = activeReservation("order-a", 10L, 3);
        Reservation b = activeReservation("order-b", 10L, 3);
        when(reservationRepository.findByReservationKey("order-a")).thenReturn(Optional.of(a));
        when(reservationRepository.findByReservationKey("order-b")).thenReturn(Optional.of(b));
        when(inventoryRepository.findByProductIdInWithLock(List.of(10L))).thenReturn(List.of(item));

        service.confirmReservation("order-a");
        service.confirmReservation("order-b");

        verify(outbox, org.mockito.Mockito.times(1)).save(any(OutboxEvent.class));
    }

    @Test
    void confirmReservationMarksActiveReservationConfirmed() {
        Reservation reservation = activeReservation("order-1", 10L, 2);
        InventoryItem item = new InventoryItem(10L, 8, "A1");
        item.setReservedQuantity(2);

        when(reservationRepository.findByReservationKey("order-1")).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByProductIdInWithLock(List.of(10L))).thenReturn(List.of(item));
        when(reservationRepository.save(reservation)).thenReturn(reservation);

        service.confirmReservation("order-1");

        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        assertNotNull(reservation.getConfirmedAt());
        assertEquals(0, item.getReservedQuantity());
        verify(inventoryRepository).save(item);
        verify(reservationRepository).save(reservation);
    }

    @Test
    void returnReservationPutsSoldQuantityBackOnce() {
        Reservation reservation = activeReservation("42", 10L, 2);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        InventoryItem item = new InventoryItem(10L, 5, "A1");

        when(reservationRepository.findByReservationKey("42")).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByProductIdInWithLock(List.of(10L))).thenReturn(List.of(item));

        service.returnReservation("42");
        assertEquals(7, item.getAvailableQuantity());
        assertEquals(ReservationStatus.RETURNED, reservation.getStatus());
        assertNotNull(reservation.getReturnedAt());

        // A retry finds RETURNED and changes nothing.
        service.returnReservation("42");
        assertEquals(7, item.getAvailableQuantity());
    }

    @Test
    void returnReservationRejectsUnsoldReservation() {
        Reservation reservation = activeReservation("42", 10L, 2);
        when(reservationRepository.findByReservationKey("42")).thenReturn(Optional.of(reservation));

        assertThrows(IllegalReservationStateException.class, () -> service.returnReservation("42"));
        verify(inventoryRepository, never()).findByProductIdInWithLock(any());
    }

    @Test
    void confirmingAReturnedReservationDoesNotTakeStockAgain() {
        Reservation reservation = activeReservation("42", 10L, 2);
        reservation.setStatus(ReservationStatus.RETURNED);
        when(reservationRepository.findByReservationKey("42")).thenReturn(Optional.of(reservation));

        service.confirmReservation("42");

        verify(inventoryRepository, never()).findByProductIdInWithLock(any());
        assertEquals(ReservationStatus.RETURNED, reservation.getStatus());
    }

    @Test
    void confirmReservationTreatsAlreadyConfirmedAsNoOp() {
        Reservation reservation = activeReservation("order-1", 10L, 2);
        reservation.setStatus(ReservationStatus.CONFIRMED);

        when(reservationRepository.findByReservationKey("order-1")).thenReturn(Optional.of(reservation));

        service.confirmReservation("order-1");

        verify(inventoryRepository, never()).findByProductIdInWithLock(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void confirmReservationRejectsReleasedReservation() {
        Reservation reservation = activeReservation("order-1", 10L, 2);
        reservation.setStatus(ReservationStatus.RELEASED);

        when(reservationRepository.findByReservationKey("order-1")).thenReturn(Optional.of(reservation));

        assertThrows(IllegalReservationStateException.class, () -> service.confirmReservation("order-1"));
        verify(inventoryRepository, never()).findByProductIdInWithLock(any());
    }

    @Test
    void confirmReservationRejectsExpiredReservation() {
        Reservation reservation = activeReservation("order-1", 10L, 2);
        reservation.setStatus(ReservationStatus.EXPIRED);

        when(reservationRepository.findByReservationKey("order-1")).thenReturn(Optional.of(reservation));

        assertThrows(IllegalReservationStateException.class, () -> service.confirmReservation("order-1"));
        verify(inventoryRepository, never()).findByProductIdInWithLock(any());
    }

    @Test
    void confirmReservationRejectsUnderflowBeforeMutation() {
        Reservation reservation = activeReservation("order-1", 10L, 2);
        InventoryItem item = new InventoryItem(10L, 8, "A1");
        item.setReservedQuantity(1);

        when(reservationRepository.findByReservationKey("order-1")).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByProductIdInWithLock(List.of(10L))).thenReturn(List.of(item));

        assertThrows(IllegalReservationStateException.class, () -> service.confirmReservation("order-1"));

        assertEquals(ReservationStatus.ACTIVE, reservation.getStatus());
        assertEquals(1, item.getReservedQuantity());
        verify(inventoryRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
    }

    private Reservation activeReservation(String key, Long productId, int quantity) {
        Reservation reservation = new Reservation(key);
        reservation.addItem(new ReservationItem(reservation, productId, quantity));
        return reservation;
    }
}
