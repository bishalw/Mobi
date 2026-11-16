package com.mobi.inventory.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * The next batch, row-locked with SKIP LOCKED: with several replicas running the
     * publisher, each takes different rows instead of all sending the same events.
     * Hibernate maps a lock timeout of -2 to SKIP LOCKED. Delivery order across rows is
     * not guaranteed (a failed row is retried later while newer ones go out), so
     * consumers must tolerate reordering, as they already tolerate redelivery.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    long deleteByStatusAndPublishedAtBefore(OutboxStatus status, LocalDateTime cutoff);
}
