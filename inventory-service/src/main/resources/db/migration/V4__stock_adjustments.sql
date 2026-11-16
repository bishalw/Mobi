-- Stock ledger: every change to available stock outside checkout is one row here, with
-- who made it and why, so the current number can always be explained.
CREATE TABLE stock_adjustments (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    product_id      BIGINT       NOT NULL,
    quantity_delta  INTEGER      NOT NULL,
    reason          ENUM('INITIAL','RECEIVED','DAMAGED','LOST','COUNT_CORRECTION') NOT NULL,
    note            VARCHAR(500),
    reference_type  VARCHAR(40),
    reference_id    VARCHAR(100),
    created_by      VARCHAR(100) NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    available_after INTEGER      NOT NULL,
    PRIMARY KEY (id),
    KEY idx_stock_adjustments_product_created (product_id, created_at),
    -- Makes event-driven adjustments (goods receipts) idempotent: a redelivered event
    -- hits this key instead of adding the stock twice. NULL references never collide.
    CONSTRAINT uk_stock_adjustments_reference UNIQUE (reference_type, reference_id, product_id)
) ENGINE=InnoDB;

-- Low-stock threshold per product; 0 means "no alert".
ALTER TABLE inventory_items ADD COLUMN reorder_level INTEGER NOT NULL DEFAULT 0;
