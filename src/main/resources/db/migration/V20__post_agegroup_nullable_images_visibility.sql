-- ageGroup nullable
ALTER TABLE therapy_posts ALTER COLUMN age_group DROP NOT NULL;

-- 게시글 이미지 테이블
CREATE TABLE therapy_post_images (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    stored_path TEXT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_therapy_post_images_post
        FOREIGN KEY (post_id) REFERENCES therapy_posts(id)
);

CREATE INDEX idx_therapy_post_images_post_order
    ON therapy_post_images (post_id, display_order ASC);

-- 공개/비공개
ALTER TABLE therapy_posts ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';
CREATE INDEX idx_therapy_posts_visibility ON therapy_posts (visibility);
