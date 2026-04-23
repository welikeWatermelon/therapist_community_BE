-- Phase 3: 치료사 전문성 지표.
-- 매일 1회 배치로 직근 WINDOW_DAYS(기본 30)일간의 활동을 집계해 z-score 기반 전문성 점수를 산출.
--
-- 왜 raw + log + z + final 모두 저장하나:
--   - "이 유저 점수가 왜 이렇지?" 질문에 중간 값 역추적 가능
--   - 공식 튜닝 시 과거 데이터에 새 가중치를 재적용해도 기존 raw 그대로 보존
--   - 가중치만 바꾸려면 raw_score만 재계산, z-score까지 바꾸려면 전체 재계산

CREATE TABLE therapist_expertise_daily (
    user_id                  BIGINT      NOT NULL,
    as_of_date               DATE        NOT NULL,        -- 집계 기준일 (이 날짜 포함, 과거 WINDOW_DAYS일 데이터)
    window_days              INT         NOT NULL DEFAULT 30,

    -- 원시 카운트 (window 내)
    posts_count              INT         NOT NULL DEFAULT 0,
    useful_received          INT         NOT NULL DEFAULT 0,
    curious_received         INT         NOT NULL DEFAULT 0,
    downloads_received       INT         NOT NULL DEFAULT 0,
    total_reactions_received INT         NOT NULL DEFAULT 0,

    -- log(1+x) 변환값 (long-tail 분포 정규화)
    log_posts                NUMERIC(10, 4),
    log_useful               NUMERIC(10, 4),
    log_curious              NUMERIC(10, 4),
    log_downloads            NUMERIC(10, 4),

    -- Laplace smoothed useful ratio: (useful + 1) / (total + 10). Beta(1,10) prior.
    useful_ratio_smoothed    NUMERIC(6, 4),

    -- z-score (전체 활동 치료사 모집단 기준)
    z_posts                  NUMERIC(8, 4),
    z_useful                 NUMERIC(8, 4),
    z_curious                NUMERIC(8, 4),
    z_downloads              NUMERIC(8, 4),
    z_useful_ratio           NUMERIC(8, 4),

    -- 가중합된 최종 점수 + 백분위
    raw_score                NUMERIC(10, 4),
    rank_percentile          NUMERIC(5, 2),              -- 0.00 ~ 100.00

    computed_at              TIMESTAMP   NOT NULL DEFAULT NOW(),

    PRIMARY KEY (user_id, as_of_date)
);

-- 최근 기준일의 top-N 랭킹 쿼리 지원.
CREATE INDEX idx_therapist_expertise_date_score
    ON therapist_expertise_daily (as_of_date DESC, raw_score DESC);

COMMENT ON TABLE therapist_expertise_daily IS
    '치료사 전문성 점수 일간 스냅샷. 30일 rolling 윈도우 활동을 z-score 정규화하여 가중합.';
COMMENT ON COLUMN therapist_expertise_daily.useful_ratio_smoothed IS
    'Laplace smoothing: (useful + α) / (total_reactions + α + β), α=1 β=9. cold start 유저의 극단 비율 방지.';
COMMENT ON COLUMN therapist_expertise_daily.rank_percentile IS
    '같은 as_of_date의 모든 치료사 중 raw_score 기준 백분위. 100이면 최상위.';

-- 진행 커서 추가. 초기값은 "어제"로 세팅 → 첫 배치가 어제치 1일만 처리.
INSERT INTO aggregation_progress (job_name, last_window_end, updated_at)
VALUES (
    'therapist_expertise_daily',
    (CURRENT_DATE - INTERVAL '1 day')::TIMESTAMP,
    NOW()
);
