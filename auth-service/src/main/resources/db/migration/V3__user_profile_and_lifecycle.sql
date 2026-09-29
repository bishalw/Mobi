-- Gives user_data the fields an administrator needs to answer "who signed up, when,
-- and is this account still allowed in" — none of which the four original columns
-- (id, username, password, role) could express.
--
-- `enabled` is BIT, not BOOLEAN/TINYINT(1): that is what Hibernate's MySQL dialect
-- emits for a boolean field, and ddl-auto=validate rejects anything else. Same
-- reasoning as the `active` column in product-service's baseline.

ALTER TABLE user_data
    ADD COLUMN email         VARCHAR(255) NULL,
    ADD COLUMN created_at    DATETIME(6)  NULL,
    ADD COLUMN last_login_at DATETIME(6)  NULL,
    ADD COLUMN enabled       BIT          NOT NULL DEFAULT b'1';

-- created_at is recovered from the outbox rather than stamped with NOW() for every
-- row. outbox_events already holds one USER_REGISTERED row per account, and its
-- created_at is the instant registration committed — the genuine signup time for
-- every user who registered after the outbox existed. Stamping all of them with the
-- migration time would have quietly fabricated that history.
UPDATE user_data u
    JOIN (
        SELECT aggregate_id, MIN(created_at) AS registered_at
        FROM outbox_events
        WHERE event_type = 'USER_REGISTERED'
        GROUP BY aggregate_id
    ) o ON o.aggregate_id = CAST(u.id AS CHAR)
SET u.created_at = o.registered_at
WHERE u.created_at IS NULL;

-- Rows with no surviving event predate the outbox, or were seeded directly like the
-- bootstrap admin. Their real signup time is unrecoverable, so they are stamped now.
UPDATE user_data SET created_at = NOW(6) WHERE created_at IS NULL;

ALTER TABLE user_data
    MODIFY created_at DATETIME(6) NOT NULL;

-- The directory's default view is newest-signups-first, paginated, so this is the
-- sort key on every page load.
CREATE INDEX ix_user_data_created_at ON user_data (created_at);

-- MySQL permits multiple NULLs under a unique index, so this stops two accounts
-- sharing a real address without forcing an address onto accounts that predate the
-- column.
CREATE UNIQUE INDEX uk_user_data_email ON user_data (email);
