-- 쪽지(채팅) 기능: conversations + messages 테이블

CREATE TABLE conversations (
    id                   BIGSERIAL    PRIMARY KEY,
    participant1_id      BIGINT       NOT NULL REFERENCES users(id),
    participant2_id      BIGINT       NOT NULL REFERENCES users(id),
    last_message_content VARCHAR(1000),
    last_message_at      TIMESTAMP    NOT NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_conversation_participants UNIQUE (participant1_id, participant2_id),
    CONSTRAINT ck_conversation_participant_order CHECK (participant1_id < participant2_id)
);

CREATE TABLE messages (
    id              BIGSERIAL    PRIMARY KEY,
    conversation_id BIGINT       NOT NULL REFERENCES conversations(id),
    sender_id       BIGINT       NOT NULL REFERENCES users(id),
    content         VARCHAR(1000) NOT NULL,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_conversation_created ON messages(conversation_id, created_at);
CREATE INDEX idx_conversations_participant1 ON conversations(participant1_id);
CREATE INDEX idx_conversations_participant2 ON conversations(participant2_id);
