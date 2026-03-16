CREATE TABLE therapy_post_reactions (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reaction_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_therapy_post_reactions_post
        FOREIGN KEY (post_id)
            REFERENCES therapy_posts(id),

    CONSTRAINT fk_therapy_post_reactions_user
        FOREIGN KEY (user_id)
            REFERENCES users(id),

    CONSTRAINT uk_therapy_post_reactions_post_user
        UNIQUE (post_id, user_id)
);

CREATE INDEX idx_therapy_post_reactions_post_id
    ON therapy_post_reactions(post_id);

CREATE INDEX idx_therapy_post_reactions_user_id
    ON therapy_post_reactions(user_id);

CREATE INDEX idx_therapy_post_reactions_type
    ON therapy_post_reactions(reaction_type);


CREATE TABLE therapy_post_comment_reactions (
    id BIGSERIAL PRIMARY KEY,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reaction_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_therapy_post_comment_reactions_comment
        FOREIGN KEY (comment_id)
            REFERENCES therapy_post_comments(id),

    CONSTRAINT fk_therapy_post_comment_reactions_user
        FOREIGN KEY (user_id)
            REFERENCES users(id),

    CONSTRAINT uk_therapy_post_comment_reactions_comment_user
        UNIQUE (comment_id, user_id)
);

CREATE INDEX idx_therapy_post_comment_reactions_comment_id
    ON therapy_post_comment_reactions(comment_id);

CREATE INDEX idx_therapy_post_comment_reactions_user_id
    ON therapy_post_comment_reactions(user_id);

CREATE INDEX idx_therapy_post_comment_reactions_type
    ON therapy_post_comment_reactions(reaction_type);