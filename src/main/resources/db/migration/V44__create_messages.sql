-- 쪽지 테이블
CREATE TABLE messages (
    id              BIGSERIAL PRIMARY KEY,
    sender_id       BIGINT NOT NULL REFERENCES users(id),
    receiver_id     BIGINT NOT NULL REFERENCES users(id),
    content         VARCHAR(1000) NOT NULL,
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    read_at         TIMESTAMP,
    deleted_by_sender   BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_by_receiver BOOLEAN NOT NULL DEFAULT FALSE,
    broadcast_id    UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 받은 쪽지함 (receiver 기준 최신순)
CREATE INDEX idx_messages_receiver_inbox
    ON messages (receiver_id, created_at DESC)
    WHERE deleted_by_receiver = FALSE;

-- 보낸 쪽지함 (sender 기준 최신순)
CREATE INDEX idx_messages_sender_outbox
    ON messages (sender_id, created_at DESC)
    WHERE deleted_by_sender = FALSE;

-- 안읽은 쪽지 수
CREATE INDEX idx_messages_receiver_unread
    ON messages (receiver_id)
    WHERE is_read = FALSE AND deleted_by_receiver = FALSE;

-- 관리자 공지 그룹 조회
CREATE INDEX idx_messages_broadcast_id
    ON messages (broadcast_id)
    WHERE broadcast_id IS NOT NULL;

-- [검증] partial index 사용 확인 쿼리 (PostgreSQL에서 실행):
-- EXPLAIN ANALYZE SELECT * FROM messages
--     WHERE receiver_id = 1 AND deleted_by_receiver = FALSE
--     ORDER BY created_at DESC LIMIT 20;
-- → idx_messages_receiver_inbox 사용 확인
--
-- EXPLAIN ANALYZE SELECT COUNT(*) FROM messages
--     WHERE receiver_id = 1 AND is_read = FALSE AND deleted_by_receiver = FALSE;
-- → idx_messages_receiver_unread 사용 확인
