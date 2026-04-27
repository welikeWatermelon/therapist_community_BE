-- pg_trgm extension (V16에서 이미 활성화됐지만 안전 차원)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- search_text 컬럼 추가 (NULL 허용 - 신규 게시글은 엔티티가 채움)
ALTER TABLE therapy_posts
    ADD COLUMN IF NOT EXISTS search_text TEXT;

-- 기존 게시글 backfill: title + content앞100자 + therapy_area 한글 + age_group 한글
-- enum 한글 값은 Java AgeGroup/TherapyArea.description 과 1:1 대응되어야 함
UPDATE therapy_posts
SET search_text = TRIM(
    COALESCE(title, '') || ' ' ||
    COALESCE(LEFT(content, 100), '') || ' ' ||
    CASE therapy_area
        WHEN 'UNSPECIFIED'         THEN '선택안함'
        WHEN 'SENSORY_INTEGRATION' THEN '감각통합'
        WHEN 'SPEECH'              THEN '언어치료'
        WHEN 'OCCUPATIONAL'        THEN '작업치료'
        WHEN 'COGNITIVE'           THEN '인지치료'
        WHEN 'PHYSICAL'            THEN '물리치료'
        WHEN 'ART'                 THEN '미술치료'
        WHEN 'MUSIC'               THEN '음악치료'
        WHEN 'PLAY'                THEN '놀이치료'
        WHEN 'BEHAVIOR'            THEN '행동치료'
        ELSE ''
    END || ' ' ||
    CASE age_group
        WHEN 'AGE_0_2'     THEN '0세 2세'
        WHEN 'AGE_3_5'     THEN '3세 5세'
        WHEN 'AGE_6_12'    THEN '6세 12세'
        WHEN 'AGE_13_18'   THEN '13세 18세'
        WHEN 'AGE_19_64'   THEN '19세 64세'
        WHEN 'AGE_65_PLUS' THEN '65세 이상'
        ELSE ''
    END
)
WHERE search_text IS NULL;

-- GIN trigram 인덱스 (similarity + ILIKE 둘 다 가속)
CREATE INDEX IF NOT EXISTS idx_therapy_posts_search_text_trgm
    ON therapy_posts USING GIN (search_text gin_trgm_ops);
