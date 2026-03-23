-- Notifications 테이블 (알림 저장소)
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT NOT NULL REFERENCES users(id),
    actor_id BIGINT NOT NULL REFERENCES users(id),
    notification_type VARCHAR(50) NOT NULL,
    reference_id BIGINT NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    message VARCHAR(500) NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- 알림 목록 조회 (최신순)
CREATE INDEX idx_notifications_recipient_created
ON notifications(recipient_id, created_at DESC);

-- 안 읽은 알림 개수 조회 (Partial Index)
CREATE INDEX idx_notifications_unread
ON notifications(recipient_id, is_read) WHERE is_read = FALSE;

-- 참조 엔티티로 알림 조회 (게시글/댓글 삭제 시)
CREATE INDEX idx_notifications_reference
ON notifications(reference_type, reference_id);
