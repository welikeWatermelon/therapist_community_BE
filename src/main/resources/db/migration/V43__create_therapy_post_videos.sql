CREATE TABLE therapy_post_videos (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    stored_path TEXT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    duration_seconds INTEGER,
    thumbnail_path TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_therapy_post_videos_post
        FOREIGN KEY (post_id) REFERENCES therapy_posts(id)
            ON DELETE CASCADE
);

CREATE INDEX idx_therapy_post_videos_post_id_created
    ON therapy_post_videos (post_id, created_at ASC);
