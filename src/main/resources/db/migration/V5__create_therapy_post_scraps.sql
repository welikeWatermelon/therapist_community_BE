CREATE TABLE therapy_post_scraps (
     id BIGSERIAL PRIMARY KEY,
     post_id BIGINT NOT NULL,
     user_id BIGINT NOT NULL,
     created_at TIMESTAMP NOT NULL,
     updated_at TIMESTAMP NOT NULL,

     CONSTRAINT fk_therapy_post_scraps_post
         FOREIGN KEY (post_id)
             REFERENCES therapy_posts(id),

     CONSTRAINT fk_therapy_post_scraps_user
         FOREIGN KEY (user_id)
             REFERENCES users(id),

     CONSTRAINT uk_therapy_post_scraps_post_user
         UNIQUE (post_id, user_id)
);

CREATE INDEX idx_therapy_post_scraps_user_id_created_at
    ON therapy_post_scraps(user_id, created_at DESC);

CREATE INDEX idx_therapy_post_scraps_post_id
    ON therapy_post_scraps(post_id);