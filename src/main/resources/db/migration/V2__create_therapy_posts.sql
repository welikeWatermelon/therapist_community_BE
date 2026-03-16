CREATE TABLE therapy_posts (
   id BIGSERIAL PRIMARY KEY,
   title VARCHAR(200) NOT NULL,
   content TEXT NOT NULL,
   therapy_area VARCHAR(50) NOT NULL,
   age_group VARCHAR(50) NOT NULL,
   view_count BIGINT NOT NULL DEFAULT 0,
   author_id BIGINT NOT NULL,
   created_at TIMESTAMP NOT NULL,
   updated_at TIMESTAMP NOT NULL,
   deleted_at TIMESTAMP NULL,

   CONSTRAINT fk_therapy_posts_author
       FOREIGN KEY (author_id)
           REFERENCES users(id)
);

CREATE INDEX idx_therapy_posts_deleted_at
    ON therapy_posts(deleted_at);

CREATE INDEX idx_therapy_posts_created_at
    ON therapy_posts(created_at DESC);

CREATE INDEX idx_therapy_posts_view_count
    ON therapy_posts(view_count DESC);

CREATE INDEX idx_therapy_posts_author_id
    ON therapy_posts(author_id);

CREATE INDEX idx_therapy_posts_therapy_area
    ON therapy_posts(therapy_area);

CREATE INDEX idx_therapy_posts_age_group
    ON therapy_posts(age_group);