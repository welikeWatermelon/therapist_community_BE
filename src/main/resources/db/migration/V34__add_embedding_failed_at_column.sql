-- 임베딩 생성 실패 추적용 타임스탬프 컬럼
-- NULL: 미시도 또는 성공 (content_embedding 으로 구분)
-- NOT NULL: 마지막 실패 시각
ALTER TABLE therapy_posts
    ADD COLUMN IF NOT EXISTS embedding_failed_at TIMESTAMP;
