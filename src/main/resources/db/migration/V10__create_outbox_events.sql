-- Outbox Events 테이블 (Outbox 패턴을 위한 이벤트 저장소)
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP
);

-- 폴링 최적화: PENDING 상태만 빠르게 조회 (Partial Index)
CREATE INDEX idx_outbox_status_created
ON outbox_events(status, created_at) WHERE status = 'PENDING';

-- 실패 이벤트 조회용
CREATE INDEX idx_outbox_failed
ON outbox_events(status, retry_count) WHERE status = 'FAILED';
