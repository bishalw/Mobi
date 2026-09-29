-- Prevent duplicate accounts, and index the outbox poll.
--
-- IF THIS MIGRATION FAILS on an existing database, duplicate usernames already
-- exist. Find them with:
--
--   SELECT username, COUNT(*) FROM user_data GROUP BY username HAVING COUNT(*) > 1;
--
-- Resolve them by hand. Which of two colliding accounts is the real one is a
-- business decision, so this migration will not guess: it fails loudly rather
-- than deleting or renaming a row someone may own.

ALTER TABLE user_data
    MODIFY username VARCHAR(255) NOT NULL,
    MODIFY password VARCHAR(255) NOT NULL,
    MODIFY role     VARCHAR(255) NOT NULL;

-- The actual fix. Without this, register()'s check-then-insert is a race: two
-- concurrent requests both find no existing user and both insert. The resulting
-- duplicate rows make findByUsername return two results, so login throws
-- IncorrectResultSizeDataAccessException forever for that username.
ALTER TABLE user_data
    ADD CONSTRAINT uk_user_data_username UNIQUE (username);

-- UserOutboxPublisher polls every 5 seconds with
-- WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 50.
-- Unindexed, that is a full scan plus a sort on every tick.
CREATE INDEX ix_outbox_events_status_created_at
    ON outbox_events (status, created_at);
