CREATE TABLE therapy_post_comments (
   id BIGSERIAL PRIMARY KEY,
   post_id BIGINT NOT NULL,
   author_id BIGINT NOT NULL,
   parent_comment_id BIGINT NULL,
   content TEXT NOT NULL,
   created_at TIMESTAMP NOT NULL,
   updated_at TIMESTAMP NOT NULL,
   deleted_at TIMESTAMP NULL,

   CONSTRAINT fk_therapy_post_comments_post
       FOREIGN KEY (post_id)
           REFERENCES therapy_posts(id),

   CONSTRAINT fk_therapy_post_comments_author
       FOREIGN KEY (author_id)
           REFERENCES users(id),

   CONSTRAINT fk_therapy_post_comments_parent
       FOREIGN KEY (parent_comment_id)
           REFERENCES therapy_post_comments(id)
);

CREATE INDEX idx_therapy_post_comments_post_id_created_at
    ON therapy_post_comments(post_id, created_at ASC);

CREATE INDEX idx_therapy_post_comments_parent_comment_id
    ON therapy_post_comments(parent_comment_id);

CREATE INDEX idx_therapy_post_comments_author_id
    ON therapy_post_comments(author_id);

CREATE INDEX idx_therapy_post_comments_deleted_at
    ON therapy_post_comments(deleted_at);