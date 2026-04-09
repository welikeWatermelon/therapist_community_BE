CREATE INDEX idx_therapy_posts_created_at_id
    ON therapy_posts(created_at DESC, id DESC)
    WHERE deleted_at IS NULL;
