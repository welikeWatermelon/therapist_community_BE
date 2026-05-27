-- 업로드 confirm 멱등성: 같은 storedKey 재시도 시 finalKey(stored_path) 가 결정적으로 동일하므로,
-- 동시 confirm race 에서 중복 INSERT 를 DB 레벨에서 거부하기 위한 유니크 제약.
-- 기존 데이터는 random UUID 기반 stored_path 라 중복 없음.
CREATE UNIQUE INDEX uq_therapy_post_images_stored_path
    ON therapy_post_images (stored_path);
