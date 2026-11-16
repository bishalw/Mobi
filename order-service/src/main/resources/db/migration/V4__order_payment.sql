-- Payment step of checkout.
--
-- payment_id links the order to the payment-service record that paid for it.
-- payment_attempts is part of the idempotency key sent to payment-service: it only
-- advances after a decline, so a retry after a timeout reuses the same key and can
-- never charge twice, while a fresh attempt with a different card gets a new one.
-- version is the JPA optimistic lock that stops two concurrent "pay" clicks from
-- both moving the same order forward.

ALTER TABLE orders
    ADD COLUMN payment_id       VARCHAR(36) NULL,
    ADD COLUMN payment_attempts INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN version          BIGINT      NOT NULL DEFAULT 0;
