-- A confirmed reservation can be returned to stock after a refund
-- (POST /inventoryapi/reservations/{key}/return).
ALTER TABLE reservations
    MODIFY status ENUM('ACTIVE','CONFIRMED','EXPIRED','RELEASED','RETURNED') NOT NULL,
    ADD COLUMN returned_at DATETIME(6) NULL;
