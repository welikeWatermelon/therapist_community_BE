-- tsvector 컬럼 추가
ALTER TABLE therapy_posts ADD COLUMN search_vector tsvector;

-- 기존 데이터 backfill
UPDATE therapy_posts
SET search_vector = to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(content, ''));

-- GIN 인덱스
CREATE INDEX idx_therapy_posts_search ON therapy_posts USING GIN(search_vector);

-- insert/update 시 자동 갱신 trigger
CREATE TRIGGER trg_therapy_posts_search_vector
    BEFORE INSERT OR UPDATE ON therapy_posts
    FOR EACH ROW EXECUTE FUNCTION
    tsvector_update_trigger(search_vector, 'pg_catalog.simple', title, content);
