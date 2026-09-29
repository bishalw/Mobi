-- Baseline: the schema exactly as Hibernate's ddl-auto previously produced it.
-- Derived from the entity mappings via Hibernate's schema exporter so that
-- `spring.jpa.hibernate.ddl-auto=validate` cannot reject it.
--
-- Table order matters: orders must exist before order_items' FK.

CREATE TABLE orders (
    id           BIGINT                                                       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT                                                       NOT NULL,
    status       ENUM('CANCELLED','CONFIRMED','INVENTORY_RESERVED','PENDING')  NOT NULL,
    total_amount DECIMAL(19,2)                                                NOT NULL,
    created_at   DATETIME(6)                                                  NOT NULL,
    updated_at   DATETIME(6)                                                  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE order_items (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    order_id              BIGINT        NOT NULL,
    product_id            BIGINT        NOT NULL,
    product_name_snapshot VARCHAR(255)  NOT NULL,
    unit_price_snapshot   DECIMAL(19,2) NOT NULL,
    quantity              INTEGER       NOT NULL,
    line_total            DECIMAL(19,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id)
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
    created_at   DATETIME(6)                 NOT NULL,
    published_at DATETIME(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB;
