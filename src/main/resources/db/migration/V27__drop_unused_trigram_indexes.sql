-- 미사용 GIN trigram 인덱스 제거
-- 사유: V25에서 search_text 컬럼 + idx_therapy_posts_search_text_trgm 인덱스가 추가된 후,
--       검색 쿼리가 search_text만 참조. title/content 개별 인덱스는 사용되지 않음.
--
-- 롤백 방법:
--   CREATE INDEX idx_therapy_posts_title_trgm ON therapy_posts USING GIN (title gin_trgm_ops);
--   CREATE INDEX idx_therapy_posts_content_trgm ON therapy_posts USING GIN (content gin_trgm_ops);

DROP INDEX IF EXISTS idx_therapy_posts_title_trgm;
DROP INDEX IF EXISTS idx_therapy_posts_content_trgm;
