-- Fingerprint of what a receipt counted, so a replayed Idempotency-Key with different
-- counts is refused instead of silently returning the first result. Null for receipts
-- recorded before this column existed.
ALTER TABLE receipts ADD COLUMN request_hash VARCHAR(64) NULL;
