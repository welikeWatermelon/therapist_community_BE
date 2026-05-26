-- 양쪽 삭제된 쪽지의 soft delete 지원
ALTER TABLE messages ADD COLUMN deleted_at TIMESTAMP;

-- 배치 정리용 인덱스: 양쪽 삭제 + soft delete된 오래된 쪽지 조회
CREATE INDEX idx_messages_soft_deleted
    ON messages (deleted_at)
    WHERE deleted_at IS NOT NULL;

-- 자기 자신에게 쪽지 발송 방지 DB 제약
ALTER TABLE messages ADD CONSTRAINT chk_no_self_message
    CHECK (sender_id != receiver_id);
