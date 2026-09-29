-- Baseline: the schema exactly as Hibernate's ddl-auto previously produced it.
--
-- Derived from the entity mappings via Hibernate's own schema exporter, not written
-- by hand, so `spring.jpa.hibernate.ddl-auto=validate` cannot reject it.
--
-- Existing databases created by ddl-auto are stamped at this version by
-- spring.flyway.baseline-on-migrate and skip this script; fresh databases run it.
-- Either way both converge from V2 onward. Deliberately does NOT include the
-- username constraints — those are a change in behaviour and belong in V2.

CREATE TABLE user_data (
    id       BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255),
    password VARCHAR(255),
    role     VARCHAR(255),
    PRIMARY KEY (id)
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
