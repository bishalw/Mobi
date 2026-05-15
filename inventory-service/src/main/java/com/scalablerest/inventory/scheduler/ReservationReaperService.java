package com.scalablerest.inventory.scheduler;

import com.scalablerest.inventory.entity.Reservation;
import com.scalablerest.inventory.entity.ReservationStatus;
import com.scalablerest.inventory.repository.ReservationRepository;
import com.scalablerest.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ReservationReaperService {

    private static final Logger log = LoggerFactory.getLogger(ReservationReaperService.class);

    private final ReservationRepository reservationRepository;
    private final InventoryService inventoryService;
    private final long ttlMinutes;

    public ReservationReaperService(ReservationRepository reservationRepository,
                                    InventoryService inventoryService,
                                    @Value("${inventory.reservation.ttl-minutes:30}") long ttlMinutes) {
        this.reservationRepository = reservationRepository;
        this.inventoryService = inventoryService;
        this.ttlMinutes = ttlMinutes;
    }

    @Scheduled(fixedDelayString = "${inventory.reservation.reaper.delay-ms:600000}")
    public void reapExpiredReservations() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(ttlMinutes);
        List<Reservation> expired = reservationRepository.findByStatusAndReservedAtBefore(ReservationStatus.ACTIVE, cutoff);

        if (expired.isEmpty()) {
            return;
        }

        log.info("Reaper found {} expired reservations (TTL={}m)", expired.size(), ttlMinutes);
        for (Reservation reservation : expired) {
            try {
                inventoryService.releaseReservation(reservation.getReservationKey());
                log.info("Reaper released expired reservation {} (reserved at {})",
                        reservation.getReservationKey(), reservation.getReservedAt());
            } catch (Exception e) {
                log.error("Reaper failed to release reservation {}: {}", reservation.getReservationKey(), e.getMessage());
            }
        }
    }
}
