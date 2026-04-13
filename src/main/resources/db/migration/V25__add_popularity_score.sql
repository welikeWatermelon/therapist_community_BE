-- 1. popularity_score 컬럼 추가
ALTER TABLE therapy_posts
    ADD COLUMN popularity_score BIGINT NOT NULL DEFAULT 0;

-- 2. 기존 데이터 초기값 세팅
--    공식: reactions * 30 + scraps * 20 + (created_at epoch초 / 8640)
UPDATE therapy_posts p
SET popularity_score = (
    COALESCE((SELECT COUNT(*) FROM therapy_post_reactions r WHERE r.post_id = p.id), 0) * 30
    + COALESCE((SELECT COUNT(*) FROM therapy_post_scraps s WHERE s.post_id = p.id), 0) * 20
    + CAST(EXTRACT(EPOCH FROM p.created_at) / 8640 AS BIGINT)
);

-- 3. 인기순 커서 기반 페이지네이션용 복합 인덱스
CREATE INDEX idx_therapy_posts_popularity_score_id
    ON therapy_posts (popularity_score DESC, id DESC)
    WHERE deleted_at IS NULL;
