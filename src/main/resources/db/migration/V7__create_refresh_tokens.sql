CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    token_family UUID NOT NULL,
    user_agent TEXT NULL,
    ip_address VARCHAR(45) NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    revoked_reason VARCHAR(50) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
            REFERENCES users(id),

    CONSTRAINT uk_refresh_tokens_token_hash
        UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id_expires_at
    ON refresh_tokens(user_id, expires_at DESC);

CREATE INDEX idx_refresh_tokens_token_family
    ON refresh_tokens(token_family);

CREATE INDEX idx_refresh_tokens_revoked_at
    ON refresh_tokens(revoked_at);