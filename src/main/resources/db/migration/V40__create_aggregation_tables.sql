-- Phase 2 집계 배치의 출력물 + 진행 커서.
-- user_events (Phase 1)를 시간 단위로 롤업해 조회·지표 산출에 쓴다.

-- ────────────────────────────────────────────────────────────
-- 시간 단위 게시글 집계.
-- 각 시각(hour)에 대해 해당 윈도우 내 이벤트를 모두 집계.
-- 집계 배치가 "해당 hour의 전체 이벤트를 DELETE → INSERT"로 재작성하므로 멱등.
-- ────────────────────────────────────────────────────────────
CREATE TABLE post_hourly_stats (
    post_id                 BIGINT    NOT NULL,
    hour                    TIMESTAMP NOT NULL,    -- date_trunc('hour', occurred_at) 값
    view_cnt                INT       NOT NULL DEFAULT 0,
    unique_viewers          INT       NOT NULL DEFAULT 0,
    reaction_like_cnt       INT       NOT NULL DEFAULT 0,
    reaction_curious_cnt    INT       NOT NULL DEFAULT 0,
    reaction_useful_cnt     INT       NOT NULL DEFAULT 0,
    scrap_cnt               INT       NOT NULL DEFAULT 0,
    comment_cnt             INT       NOT NULL DEFAULT 0,
    download_cnt            INT       NOT NULL DEFAULT 0,
    unique_downloaders      INT       NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id, hour)
);

-- 랭킹/인기 쿼리 패턴 (시간 역순으로 특정 hour의 상위 N개 post 조회).
CREATE INDEX idx_post_hourly_stats_hour ON post_hourly_stats (hour DESC);

COMMENT ON TABLE post_hourly_stats IS
    'user_events를 1시간 윈도우로 롤업. 각 hour는 raw 이벤트로부터 완전 재계산되어 멱등 보장.';
COMMENT ON COLUMN post_hourly_stats.unique_viewers IS
    '해당 hour에 최소 1회 이상 조회한 distinct user 수. view_cnt는 중복 포함 원시 카운트.';

-- ────────────────────────────────────────────────────────────
-- 집계 배치 진행 커서.
-- 각 잡은 "어디까지 처리했는가"를 last_window_end에 기록.
-- 재시작/수동 백필 시 이 값을 조정하면 해당 시점부터 재집계.
-- ────────────────────────────────────────────────────────────
CREATE TABLE aggregation_progress (
    job_name          VARCHAR(100) PRIMARY KEY,
    last_window_end   TIMESTAMP    NOT NULL,    -- 처리 완료된 가장 최근 hour의 "끝" 경계 (exclusive)
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE aggregation_progress IS
    '집계 잡별 진행 상황. last_window_end는 이미 완료된 가장 최근 hour의 상한 (이 값 이전까지 집계 완료).';

-- 잡 등록은 Flyway에서 초기값만. last_window_end를 "now() - 1h의 시작"으로 두어
-- 첫 배치가 현재 시각 이전의 한 hour만 채우도록 함 (빈 테이블 백필 폭주 방지).
INSERT INTO aggregation_progress (job_name, last_window_end, updated_at)
VALUES (
    'post_hourly_stats',
    date_trunc('hour', NOW()) - INTERVAL '1 hour',
    NOW()
);
