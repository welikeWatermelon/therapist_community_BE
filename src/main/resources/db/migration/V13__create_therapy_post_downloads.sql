CREATE TABLE therapy_post_downloads (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    first_downloaded_at TIMESTAMP NOT NULL,
    last_downloaded_at TIMESTAMP NOT NULL,
    download_count BIGINT NOT NULL DEFAULT 1 CHECK (download_count > 0),

    CONSTRAINT fk_therapy_post_downloads_post
        FOREIGN KEY (post_id)
            REFERENCES therapy_posts(id),
    CONSTRAINT fk_therapy_post_downloads_user
        FOREIGN KEY (user_id)
            REFERENCES users(id),
    CONSTRAINT uk_therapy_post_downloads_post_user
        UNIQUE (post_id, user_id)
);

CREATE INDEX idx_therapy_post_downloads_user_last_downloaded_at
    ON therapy_post_downloads(user_id, last_downloaded_at DESC);

CREATE INDEX idx_therapy_post_downloads_post_last_downloaded_at
    ON therapy_post_downloads(post_id, last_downloaded_at DESC);
