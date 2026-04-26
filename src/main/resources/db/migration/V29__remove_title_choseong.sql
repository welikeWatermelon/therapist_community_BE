-- 초성검색 기능 제거: titleChoseong 컬럼 + 인덱스 삭제
-- 사유: 초성 검색 기능 미사용 (Java 코드에서 populate하지 않는 Dead Field)

DROP INDEX IF EXISTS idx_therapy_posts_title_choseong;
ALTER TABLE therapy_posts DROP COLUMN IF EXISTS title_choseong;
