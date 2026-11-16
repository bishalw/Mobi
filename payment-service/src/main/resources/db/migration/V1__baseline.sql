-- payment-service schema. Column types follow what Hibernate's MySQL dialect emits for
-- the entity mappings, since ddl-auto=validate checks them on startup.

CREATE TABLE payments (
    id                 BIGINT                                                 NOT NULL AUTO_INCREMENT,
    payment_id         VARCHAR(36)                                            NOT NULL,
    idempotency_key    VARCHAR(100)                                           NOT NULL,
    order_id           BIGINT                                                 NOT NULL,
    user_id            BIGINT                                                 NOT NULL,
    amount             DECIMAL(19,2)                                          NOT NULL,
    currency           VARCHAR(3)                                             NOT NULL,
    status             ENUM('DECLINED','PROCESSING','REFUNDED','SUCCEEDED')   NOT NULL,
    provider           VARCHAR(30)                                            NOT NULL,
    provider_reference VARCHAR(100),
    card_brand         VARCHAR(20),
    card_last4         VARCHAR(4),
    failure_code       VARCHAR(50),
    failure_message    VARCHAR(255),
    refund_reason      VARCHAR(255),
    refunded_at        DATETIME(6),
    version            BIGINT                                                 NOT NULL,
    created_at         DATETIME(6)                                            NOT NULL,
    updated_at         DATETIME(6)                                            NOT NULL,
    PRIMARY KEY (id),
    -- The unique key on idempotency_key is the concurrency control for charging: the
    -- second of two simultaneous requests fails its INSERT instead of reaching the provider.
    CONSTRAINT uk_payments_payment_id UNIQUE (payment_id),
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key)
) ENGINE=InnoDB;

CREATE INDEX ix_payments_order_id_status ON payments (order_id, status);
CREATE INDEX ix_payments_created_at ON payments (created_at);

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
