-- A read-only projection of the accounts owned by auth-service, maintained from the
-- user-events topic.
--
-- It exists so that "customers ranked by order count" can be answered with one indexed
-- query in one service. Without a local copy of the identity, ranking would mean pulling
-- every order and every user across the service boundary and sorting them in the caller —
-- which cannot be paginated and gets slower with every signup.
--
-- This table is never written by order-service's own business logic. auth-service remains
-- the source of truth; a row here is a cache of what it published, and the whole table can
-- be rebuilt from the replay endpoint.

CREATE TABLE user_ref (
    user_id    BIGINT       NOT NULL,
    username   VARCHAR(255) NOT NULL,
    email      VARCHAR(255),
    enabled    BIT          NOT NULL,
    -- The account's signup time, not the time this row was written.
    created_at DATETIME(6)  NOT NULL,
    -- occurredAt of the event this row was last built from. Used to discard events that
    -- arrive out of order, which Kafka permits across partitions and on redelivery.
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB;

-- Supports searching the customer list by name without a full scan.
CREATE INDEX ix_user_ref_username ON user_ref (username);
