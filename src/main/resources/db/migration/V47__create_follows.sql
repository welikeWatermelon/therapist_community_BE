CREATE TABLE follows (
    id          BIGSERIAL PRIMARY KEY,
    follower_id BIGINT NOT NULL REFERENCES users(id),
    following_id BIGINT NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_follows_follower_following UNIQUE (follower_id, following_id),
    CONSTRAINT chk_follows_no_self CHECK (follower_id <> following_id)
);

CREATE INDEX idx_follows_follower_id ON follows(follower_id, created_at DESC);
CREATE INDEX idx_follows_following_id ON follows(following_id);
