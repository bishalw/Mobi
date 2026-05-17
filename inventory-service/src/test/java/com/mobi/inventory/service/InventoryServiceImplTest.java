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

class InventoryServiceImplTest {

    private InventoryRepository inventoryRepository;
    private ReservationRepository reservationRepository;
    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        inventoryRepository = mock(InventoryRepository.class);
        reservationRepository = mock(ReservationRepository.class);
        service = new InventoryServiceImpl(inventoryRepository, reservationRepository);
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
