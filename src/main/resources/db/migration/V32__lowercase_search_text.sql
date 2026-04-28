-- searchText를 소문자로 정규화: GIN trigram 인덱스(bare LIKE)를 유지하면서 대소문자 무시 검색 달성
UPDATE therapy_posts
SET search_text = LOWER(search_text)
WHERE search_text IS NOT NULL;
