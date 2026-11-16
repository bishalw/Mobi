-- The W3C traceparent of the request that wrote each outbox row, so the scheduled
-- publisher can continue that trace instead of starting a new one. 55 characters is
-- the fixed length of a version-00 traceparent.
ALTER TABLE outbox_events ADD COLUMN traceparent VARCHAR(55) NULL;
