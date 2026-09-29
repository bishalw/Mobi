-- Baseline: the schema exactly as Hibernate's ddl-auto previously produced it.
-- Derived from the entity mappings via Hibernate's schema exporter so that
-- `spring.jpa.hibernate.ddl-auto=validate` cannot reject it.
--
-- inventory_items.version backs @Version optimistic locking and is nullable,
-- matching Hibernate's output — a null version means "not yet written".
-- Table order matters: reservations must exist before reservation_items' FK.

CREATE TABLE inventory_items (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    product_id         BIGINT      NOT NULL,
    available_quantity INTEGER     NOT NULL,
    reserved_quantity  INTEGER     NOT NULL,
    warehouse_location VARCHAR(100),
    version            BIGINT,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_items_product_id UNIQUE (product_id)
) ENGINE=InnoDB;

CREATE TABLE reservations (
    id              BIGINT                                            NOT NULL AUTO_INCREMENT,
    reservation_key VARCHAR(100)                                      NOT NULL,
    status          ENUM('ACTIVE','CONFIRMED','EXPIRED','RELEASED')    NOT NULL,
    reserved_at     DATETIME(6)                                       NOT NULL,
    released_at     DATETIME(6),
    confirmed_at    DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_reservations_reservation_key UNIQUE (reservation_key)
) ENGINE=InnoDB;

CREATE TABLE reservation_items (
    id             BIGINT  NOT NULL AUTO_INCREMENT,
    reservation_id BIGINT  NOT NULL,
    product_id     BIGINT  NOT NULL,
    quantity       INTEGER NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reservation_items_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations (id)
) ENGINE=InnoDB;
