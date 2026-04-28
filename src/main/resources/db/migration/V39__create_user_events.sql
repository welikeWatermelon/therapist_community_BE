-- 분석/매칭 시스템의 이벤트 수집 레이어.
-- append-only + 고빈도 write → 월 단위 RANGE 파티셔닝.
-- 파티션 프루닝으로 조회 속도 확보, 오래된 파티션은 DROP으로 보존기간 관리.

CREATE TABLE user_events (
    id          BIGSERIAL,
    user_id     BIGINT      NOT NULL,
    event_type  VARCHAR(50) NOT NULL,
    target_type VARCHAR(30),
    target_id   BIGINT,
    metadata    JSONB,
    occurred_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- 부트스트랩 파티션 (2026-04 ~ 2026-07).
-- 이후 파티션은 Phase 2의 @Scheduled 배치가 매월 자동 생성.
CREATE TABLE user_events_2026_04 PARTITION OF user_events
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE user_events_2026_05 PARTITION OF user_events
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE user_events_2026_06 PARTITION OF user_events
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE user_events_2026_07 PARTITION OF user_events
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');

-- 조회 패턴별 인덱스.
-- 파티션된 부모에 인덱스를 만들면 각 파티션에 자동 전파됨 (PG 11+).
CREATE INDEX idx_user_events_user_occurred
    ON user_events (user_id, occurred_at DESC);

CREATE INDEX idx_user_events_type_occurred
    ON user_events (event_type, occurred_at DESC);

CREATE INDEX idx_user_events_target
    ON user_events (target_type, target_id, occurred_at DESC);

COMMENT ON TABLE user_events IS
    '사용자 행동 이벤트 로그. 분석/매칭/전문성 지표 산출의 원천. 월 단위 RANGE 파티셔닝.';
COMMENT ON COLUMN user_events.event_type IS
    'POST_VIEW, POST_REACT, POST_SCRAP, ATTACHMENT_DOWNLOAD, COMMENT_CREATE 등';
COMMENT ON COLUMN user_events.target_type IS
    'POST, COMMENT, ATTACHMENT — target_id와 조합하여 대상 특정';
COMMENT ON COLUMN user_events.metadata IS
    '이벤트별 부가 필드 (reaction_type, duration_ms, therapy_area 등). 스키마 확장 시 마이그레이션 불필요.';
