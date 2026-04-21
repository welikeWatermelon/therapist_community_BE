-- 프로필 이미지 URL을 "파일명만" 저장하도록 정규화.
--
-- 기존 데이터는 세 가지 형태가 섞여있을 수 있음:
--   1) 'http://localhost:8080/api/v1/me/profile-image/profile-images/abc.jpg'  (잘못 박힌 localhost)
--   2) 'https://api.도메인/api/v1/me/profile-image/profile-images/abc.jpg'       (정상 배포본)
--   3) 'profile-images/abc.jpg'                                                  (혹시 모를 혼재)
--
-- 목표: 'abc.jpg' 하나로 통일.
--
-- 변환 순서:
--   (1) 'http(s)://{host}/api/v1/me/profile-image/' prefix 제거
--   (2) 'profile-images/' prefix 제거
--
-- 변환 후 코드 경로에서는:
--   - 응답 조립: ProfileImageUrlAssembler.toFullUrl("abc.jpg") → '{baseUrl}/api/v1/me/profile-image/abc.jpg'
--   - 스토리지 접근: ProfileImageUrlAssembler.toStoragePath("abc.jpg") → 'profile-images/abc.jpg'

UPDATE users
SET profile_image_url = regexp_replace(
        profile_image_url,
        '^https?://[^/]+/api/v1/me/profile-image/',
        ''
    )
WHERE profile_image_url ~ '^https?://';

UPDATE users
SET profile_image_url = regexp_replace(
        profile_image_url,
        '^profile-images/',
        ''
    )
WHERE profile_image_url LIKE 'profile-images/%';
