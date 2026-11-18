-- Login hardening.
--
-- refresh_tokens backs short-lived access tokens: the JWT now lives 15 minutes, and a
-- long-lived refresh token (an httpOnly cookie the browser's JavaScript cannot read)
-- silently mints new ones. Only the SHA-256 of each token is stored, so a leaked copy
-- of this table cannot be replayed.
--
-- family_id groups every token descended from one login. Tokens rotate on every
-- refresh, so a token that is presented again after being replaced can only be a copy:
-- the whole family is revoked and the thief and the victim are both signed out.

CREATE TABLE refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash  VARCHAR(64)  NOT NULL,
    user_id     BIGINT       NOT NULL,
    family_id   VARCHAR(36)  NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    revoked_at  DATETIME(6),
    replaced_at DATETIME(6),
    user_agent  VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash)
) ENGINE=InnoDB;

CREATE INDEX ix_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);

ALTER TABLE user_data
    ADD COLUMN phone                 VARCHAR(20) NULL,
    ADD COLUMN failed_login_attempts INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN locked_until          DATETIME(6) NULL;

-- Same trace hand-off through the outbox as order-service; see OutboxTracing.
ALTER TABLE outbox_events ADD COLUMN traceparent VARCHAR(55) NULL;
