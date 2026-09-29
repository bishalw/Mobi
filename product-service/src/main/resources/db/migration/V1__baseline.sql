-- Baseline: the schema exactly as Hibernate's ddl-auto previously produced it.
-- Derived from the entity mappings via Hibernate's schema exporter so that
-- `spring.jpa.hibernate.ddl-auto=validate` cannot reject it.
--
-- `active BIT NOT NULL` is what Hibernate's MySQL dialect emits for a boolean
-- field; TINYINT(1) would also work at runtime but would differ from the
-- existing databases this baseline has to match.

CREATE TABLE product_records (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    sku          VARCHAR(64)   NOT NULL,
    product_name VARCHAR(255)  NOT NULL,
    description  VARCHAR(1000),
    price        DECIMAL(19,2) NOT NULL,
    active       BIT           NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_records_sku UNIQUE (sku)
) ENGINE=InnoDB;
