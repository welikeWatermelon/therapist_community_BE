CREATE TABLE job_posts (
    id                BIGSERIAL    PRIMARY KEY,
    author_id         BIGINT       NOT NULL,
    organization_name VARCHAR(100) NOT NULL,
    content           TEXT         NOT NULL,
    therapy_area      VARCHAR(50)  NOT NULL,
    employment_type   VARCHAR(30)  NOT NULL,
    region            VARCHAR(30)  NOT NULL,
    salary_text       VARCHAR(100),
    qualification     TEXT,
    preferred         TEXT,
    source_url        VARCHAR(500) NOT NULL,
    deadline_date     DATE         NOT NULL,
    closed_manually   BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMP,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,

    CONSTRAINT fk_job_posts_author
        FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE INDEX idx_job_posts_deadline_id ON job_posts (deadline_date, id);
CREATE INDEX idx_job_posts_author_id ON job_posts (author_id);
