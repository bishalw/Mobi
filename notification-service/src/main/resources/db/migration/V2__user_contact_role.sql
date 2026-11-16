-- The account's role, from user-events, so staff notifications (purchase orders, low
-- stock) can go to every admin or every warehouse user. Null until the account's next
-- event; auth-service republishes its bootstrap accounts on every start.
ALTER TABLE user_contacts ADD COLUMN role VARCHAR(30) NULL;
CREATE INDEX ix_user_contacts_role ON user_contacts (role);
