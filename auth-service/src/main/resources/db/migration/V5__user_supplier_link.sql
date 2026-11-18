-- Links a SUPPLIER account to the supplier it acts for (a procurement-service id).
-- Null for every other role. No foreign key: suppliers live in another service's database.
ALTER TABLE user_data ADD COLUMN supplier_id BIGINT NULL;
