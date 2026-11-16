-- Whether a refund also returned the order's items to stock.
ALTER TABLE orders
    ADD COLUMN restocked BIT(1) NOT NULL DEFAULT b'0';
