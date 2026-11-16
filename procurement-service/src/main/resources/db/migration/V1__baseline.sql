-- procurement-service schema. Column types follow what Hibernate's MySQL dialect emits for
-- the entity mappings, since ddl-auto=validate checks them on startup (BIT for booleans,
-- ENUM for @Enumerated(STRING), DATETIME(6) for LocalDateTime).

CREATE TABLE suppliers (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(150) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    active        BIT          NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_suppliers_name UNIQUE (name)
) ENGINE=InnoDB;

-- What a supplier sells us and at what price. A purchase order line must name a product
-- in its supplier's catalogue; its unit cost defaults to the price here.
CREATE TABLE supplier_products (
    supplier_id  BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    supplier_sku VARCHAR(100),
    unit_cost    DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (supplier_id, product_id),
    CONSTRAINT fk_supplier_products_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id)
) ENGINE=InnoDB;

CREATE TABLE purchase_orders (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    po_number        VARCHAR(40)  NOT NULL,
    supplier_id      BIGINT       NOT NULL,
    status           ENUM('ACCEPTED','CANCELLED','DRAFT','PARTIALLY_RECEIVED','RECEIVED','REJECTED','SENT','SHIPPED') NOT NULL,
    expected_date    DATE,
    notes            VARCHAR(1000),
    status_note      VARCHAR(500),
    created_by       VARCHAR(100) NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    sent_at          DATETIME(6),
    accepted_at      DATETIME(6),
    rejected_at      DATETIME(6),
    cancelled_at     DATETIME(6),
    closed_at        DATETIME(6),
    version          BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_purchase_orders_po_number UNIQUE (po_number),
    CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id)
) ENGINE=InnoDB;

CREATE INDEX ix_purchase_orders_status_created ON purchase_orders (status, created_at);
CREATE INDEX ix_purchase_orders_supplier_created ON purchase_orders (supplier_id, created_at);

CREATE TABLE purchase_order_lines (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    po_id             BIGINT        NOT NULL,
    product_id        BIGINT        NOT NULL,
    quantity_ordered  INTEGER       NOT NULL,
    unit_cost         DECIMAL(12,2) NOT NULL,
    quantity_shipped  INTEGER       NOT NULL,
    quantity_received INTEGER       NOT NULL,
    quantity_damaged  INTEGER       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_purchase_order_lines_product UNIQUE (po_id, product_id),
    CONSTRAINT fk_purchase_order_lines_po FOREIGN KEY (po_id) REFERENCES purchase_orders (id)
) ENGINE=InnoDB;

CREATE TABLE shipment_notices (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    po_id            BIGINT       NOT NULL,
    carrier          VARCHAR(100) NOT NULL,
    tracking_number  VARCHAR(100),
    expected_arrival DATE,
    created_by       VARCHAR(100) NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_shipment_notices_po FOREIGN KEY (po_id) REFERENCES purchase_orders (id)
) ENGINE=InnoDB;

CREATE TABLE shipment_notice_lines (
    id               BIGINT  NOT NULL AUTO_INCREMENT,
    notice_id        BIGINT  NOT NULL,
    po_line_id       BIGINT  NOT NULL,
    quantity_shipped INTEGER NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_shipment_notice_lines_notice FOREIGN KEY (notice_id) REFERENCES shipment_notices (id),
    CONSTRAINT fk_shipment_notice_lines_po_line FOREIGN KEY (po_line_id) REFERENCES purchase_order_lines (id)
) ENGINE=InnoDB;

-- Filled by receiving (spec phase 4). receipt_key is the Idempotency-Key the warehouse
-- client sends, so a retried submit can't count the same goods twice.
CREATE TABLE receipts (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    receipt_key VARCHAR(100) NOT NULL,
    po_id       BIGINT       NOT NULL,
    received_by VARCHAR(100) NOT NULL,
    received_at DATETIME(6)  NOT NULL,
    note        VARCHAR(500),
    PRIMARY KEY (id),
    CONSTRAINT uk_receipts_receipt_key UNIQUE (receipt_key),
    CONSTRAINT fk_receipts_po FOREIGN KEY (po_id) REFERENCES purchase_orders (id)
) ENGINE=InnoDB;

CREATE TABLE receipt_lines (
    id               BIGINT  NOT NULL AUTO_INCREMENT,
    receipt_id       BIGINT  NOT NULL,
    po_line_id       BIGINT  NOT NULL,
    quantity_good    INTEGER NOT NULL,
    quantity_damaged INTEGER NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_receipt_lines_receipt FOREIGN KEY (receipt_id) REFERENCES receipts (id),
    CONSTRAINT fk_receipt_lines_po_line FOREIGN KEY (po_line_id) REFERENCES purchase_order_lines (id)
) ENGINE=InnoDB;

CREATE TABLE outbox_events (
    id           BIGINT                      NOT NULL AUTO_INCREMENT,
    topic        VARCHAR(100)                NOT NULL,
    event_key    VARCHAR(100)                NOT NULL,
    aggregate_id VARCHAR(100)                NOT NULL,
    event_type   VARCHAR(100)                NOT NULL,
    payload_json TEXT                        NOT NULL,
    status       ENUM('PENDING','PUBLISHED') NOT NULL,
    attempts     INTEGER                     NOT NULL,
    last_error   VARCHAR(500),
    traceparent  VARCHAR(55),
    created_at   DATETIME(6)                 NOT NULL,
    published_at DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE INDEX ix_outbox_events_status_created_at ON outbox_events (status, created_at);
