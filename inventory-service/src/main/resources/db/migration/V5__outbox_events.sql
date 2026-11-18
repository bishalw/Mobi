-- Outbox for inventory-events (LOW_STOCK), same shape as the other services' outboxes.

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
