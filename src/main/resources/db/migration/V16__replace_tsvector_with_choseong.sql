-- tsvector 인프라 제거
DROP TRIGGER IF EXISTS trg_therapy_posts_search_vector ON therapy_posts;
DROP INDEX IF EXISTS idx_therapy_posts_search;
ALTER TABLE therapy_posts DROP COLUMN IF EXISTS search_vector;

-- 초성 검색용 컬럼 추가
ALTER TABLE therapy_posts ADD COLUMN IF NOT EXISTS title_choseong VARCHAR(200);

-- title_choseong 인덱스 (LIKE prefix 검색용)
CREATE INDEX IF NOT EXISTS idx_therapy_posts_title_choseong ON therapy_posts (title_choseong varchar_pattern_ops);

-- title + content 검색용 trigram 인덱스 (ILIKE '%keyword%' 성능 개선)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_therapy_posts_title_trgm ON therapy_posts USING GIN (title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_therapy_posts_content_trgm ON therapy_posts USING GIN (content gin_trgm_ops);
