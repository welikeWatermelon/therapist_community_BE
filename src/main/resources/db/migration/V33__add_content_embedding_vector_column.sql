-- pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- 임베딩 컬럼 추가 (text-embedding-3-small: 1536 차원)
ALTER TABLE therapy_posts
    ADD COLUMN IF NOT EXISTS content_embedding vector(1536);

-- HNSW 인덱스 (코사인 거리)
CREATE INDEX IF NOT EXISTS idx_therapy_posts_content_embedding_hnsw
    ON therapy_posts USING hnsw (content_embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
