CREATE TABLE IF NOT EXISTS user_agreements (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    agreement_type VARCHAR(50) NOT NULL,
    version VARCHAR(10) NOT NULL,
    agreed_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_user_agreements_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_user_agreements_user_type
        UNIQUE (user_id, agreement_type)
);

CREATE INDEX IF NOT EXISTS idx_user_agreements_user_id ON user_agreements (user_id);
