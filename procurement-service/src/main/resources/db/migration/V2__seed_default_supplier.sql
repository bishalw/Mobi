-- The one supplier the demo runs with. auth-service's seeded `supplier` login is linked to
-- id 1 by config (SUPPLIER_BOOTSTRAP_SUPPLIER_ID), so the id is fixed here. Its catalogue
-- is filled by scripts/seed-phones.sh once products exist.
INSERT INTO suppliers (id, name, contact_email, active, created_at)
VALUES (1, 'Mobi Distribution', 'orders@mobi-distribution.test', b'1', NOW(6));
