-- notification-service's own schema: where to reach each user, and what was sent.

CREATE TABLE user_contacts (
    user_id    BIGINT       NOT NULL,
    username   VARCHAR(255) NOT NULL,
    email      VARCHAR(255),
    phone      VARCHAR(20),
    enabled    BIT          NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB;

CREATE TABLE notifications (
    id         BIGINT                 NOT NULL AUTO_INCREMENT,
    event_id   VARCHAR(64)            NOT NULL,
    channel    ENUM('EMAIL','SMS')    NOT NULL,
    template   VARCHAR(50)            NOT NULL,
    user_id    BIGINT,
    recipient  VARCHAR(255),
    status     ENUM('SENT','SKIPPED') NOT NULL,
    detail     VARCHAR(255),
    created_at DATETIME(6)            NOT NULL,
    PRIMARY KEY (id),
    -- At most one message per event per channel, however often Kafka redelivers it.
    CONSTRAINT uk_notifications_event_channel UNIQUE (event_id, channel)
) ENGINE=InnoDB;

CREATE INDEX ix_notifications_user_id ON notifications (user_id);
