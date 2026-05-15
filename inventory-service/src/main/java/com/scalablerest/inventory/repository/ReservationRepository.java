package com.scalablerest.inventory.repository;

import com.scalablerest.inventory.entity.Reservation;
import com.scalablerest.inventory.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByReservationKey(String reservationKey);

    List<Reservation> findByStatusAndReservedAtBefore(ReservationStatus status, LocalDateTime cutoff);
}
