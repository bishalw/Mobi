-- ReservationReaperService runs findByStatusAndReservedAtBefore every 10 minutes
-- to release reservations whose TTL has expired. Unindexed, that is a full scan of
-- reservations on every tick, growing with order volume.
CREATE INDEX ix_reservations_status_reserved_at
    ON reservations (status, reserved_at);
