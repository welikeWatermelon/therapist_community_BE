ALTER TABLE therapy_posts
    ADD COLUMN IF NOT EXISTS post_type VARCHAR(50) NOT NULL DEFAULT 'COMMUNITY';

CREATE INDEX IF NOT EXISTS idx_therapy_posts_post_type
    ON therapy_posts(post_type);

CREATE TABLE therapy_post_attachments (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    stored_path TEXT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    extension VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_therapy_post_attachments_post
        FOREIGN KEY (post_id)
            REFERENCES therapy_posts(id)
            ON DELETE CASCADE
);

CREATE INDEX idx_therapy_post_attachments_post_id_created_at
    ON therapy_post_attachments(post_id, created_at ASC);
