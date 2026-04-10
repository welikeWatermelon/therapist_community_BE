CREATE TABLE post_ai_comment_jobs (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    comment_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    review_status VARCHAR(20),
    source_mode VARCHAR(20),
    draft_comment TEXT,
    retrieval_context_json JSONB,
    confidence_score DOUBLE PRECISION,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP,
    last_error_code VARCHAR(50),
    last_error_message TEXT,
    reviewed_by_user_id BIGINT,
    reviewed_at TIMESTAMP,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_ai_comment_jobs_post
        FOREIGN KEY (post_id) REFERENCES therapy_posts(id),
    CONSTRAINT fk_ai_comment_jobs_requested_by
        FOREIGN KEY (requested_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_ai_comment_jobs_comment
        FOREIGN KEY (comment_id) REFERENCES therapy_post_comments(id),
    CONSTRAINT fk_ai_comment_jobs_reviewed_by
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id),
    CONSTRAINT uk_ai_comment_jobs_post
        UNIQUE (post_id)
);

CREATE INDEX idx_ai_comment_jobs_status_next_attempt
    ON post_ai_comment_jobs(status, next_attempt_at);
