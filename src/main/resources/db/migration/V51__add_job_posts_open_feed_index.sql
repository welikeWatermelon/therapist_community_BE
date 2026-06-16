-- 공개 목록(OPEN 피드) 핫패스 최적화: deleted_at IS NULL AND closed_manually = false 조건에서
-- deadline_date ASC, id ASC 키셋 정렬을 커버하는 partial index.
-- soft-deleted/조기마감 row를 인덱스에서 제외해 데이터 증가 시 스캔량을 줄인다.
-- (CLOSED 피드는 OR 조건/콜드패스라 기존 idx_job_posts_deadline_id 활용 + 볼륨 보고 후속 결정)
CREATE INDEX idx_job_posts_open_feed
    ON job_posts (deadline_date, id)
    WHERE deleted_at IS NULL AND closed_manually = false;
