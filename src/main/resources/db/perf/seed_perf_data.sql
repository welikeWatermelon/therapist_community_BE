-- ========================================================
-- 성능 테스트용 대용량 seed data 생성 스크립트
--
-- ⚠️ 경고: 이 스크립트는 **모든 도메인 데이터를 TRUNCATE** 합니다.
--    로컬 개발 DB에서만 실행하세요. 운영/스테이징 DB에서 절대 실행 금지.
--
-- 실행:
--   docker exec -i builders-db psql -U builders -d builders \
--       < src/main/resources/db/perf/seed_perf_data.sql
--
-- ── 볼륨 파라미터 (아래 숫자만 조정해서 규모 바꿀 수 있음) ──────────────
-- 기본값: 게시글 10k 기준 (빠름, baseline용)
-- 10배 스케일 테스트: N_POSTS=100000 N_COMMENTS_*=×10 N_REACTIONS_TRY=7000000 ...
--   (메모리 · 디스크 여유 있는지 먼저 확인. 대략 2~5분 소요)
-- ========================================================

\set N_USERS          1000
\set N_POSTS          10000
\set N_COMMENTS_ROOT  80000
\set N_COMMENTS_REPLY 20000
\set N_REACTIONS_TRY  700000
\set N_SCRAPS_TRY     70000

SET client_min_messages = NOTICE;
SELECT setseed(0.42);  -- 고정 시드 → 재현성

\echo '→ Truncating existing data...'

TRUNCATE
    therapy_post_comment_reactions,
    therapy_post_reactions,
    therapy_post_scraps,
    therapy_post_comments,
    therapy_post_images,
    therapy_post_attachments,
    therapy_post_downloads,
    therapy_posts,
    therapist_verifications,
    notifications,
    refresh_tokens,
    user_agreements,
    users
RESTART IDENTITY CASCADE;

\echo '→ Seeding users...'

INSERT INTO users (email, password_hash, nickname, role, created_at, updated_at)
SELECT
    'perf-' || i || '@test.local' AS email,
    '$2a$10$CwTycUXWue0Thq9StjUM0uJ8uO.1VeGHVG5jEZVv6nLxZlWQb/ioe' AS password_hash,
    CASE (i % 8)
        WHEN 0 THEN '판다' WHEN 1 THEN '고래' WHEN 2 THEN '나무늘보' WHEN 3 THEN '여우'
        WHEN 4 THEN '호랑이' WHEN 5 THEN '치타' WHEN 6 THEN '독수리' ELSE '펭귄'
    END || '#' || lpad(i::text, 6, '0') AS nickname,
    CASE
        WHEN i <= (:N_USERS / 50)  THEN 'ADMIN'        -- 2%
        WHEN i <= (:N_USERS / 3)   THEN 'THERAPIST'    -- ~33%
        ELSE 'USER'                                    -- 나머지
    END AS role,
    NOW() - ((random() * 365)::int || ' days')::interval AS created_at,
    NOW() AS updated_at
FROM generate_series(1, :N_USERS) AS i;

\echo '→ Seeding therapy_posts...'

INSERT INTO therapy_posts (
    title, content, therapy_area, age_group,
    view_count, author_id, visibility, popularity_score,
    created_at, updated_at
)
SELECT
    '성능테스트 게시글 #' || i AS title,
    '<p>' ||
    repeat(
        (ARRAY[
            '감각통합치료 세션 후기를 공유합니다. ',
            '언어치료 접근법 중 효과적이었던 케이스입니다. ',
            '작업치료 도구 추천 부탁드립니다. ',
            '인지치료 과정에서 발견한 패턴 정리. ',
            '놀이치료 중 자주 쓰는 아이스브레이커 목록. '
        ])[1 + floor(random() * 5)::int],
        1 + (i % 5)
    ) || '</p>' AS content,
    (ARRAY['UNSPECIFIED','SPEECH','OCCUPATIONAL','COGNITIVE','PLAY','SENSORY_INTEGRATION','PHYSICAL','ART','MUSIC','BEHAVIOR'])[1 + floor(random() * 10)::int] AS therapy_area,
    CASE WHEN random() < 0.7 THEN
        (ARRAY['UNSPECIFIED','AGE_0_2','AGE_3_5','AGE_6_12','AGE_13_18','AGE_19_64','AGE_65_PLUS'])[1 + floor(random() * 7)::int]
    ELSE NULL END AS age_group,
    (random() * 500)::bigint AS view_count,
    1 + floor(random() * :N_USERS)::int AS author_id,
    CASE WHEN random() < 0.85 THEN 'PUBLIC' ELSE 'PRIVATE' END AS visibility,
    0::bigint AS popularity_score,
    NOW() - ((random() * 365 * 24 * 3600)::int || ' seconds')::interval AS created_at,
    NOW() AS updated_at
FROM generate_series(1, :N_POSTS) AS i;

\echo '→ Seeding root comments...'

INSERT INTO therapy_post_comments (
    post_id, author_id, parent_comment_id, content,
    created_at, updated_at
)
SELECT
    1 + floor(random() * :N_POSTS)::int AS post_id,
    1 + floor(random() * :N_USERS)::int AS author_id,
    NULL,
    '테스트 댓글 본문 ' || i,
    NOW() - ((random() * 180 * 24 * 3600)::int || ' seconds')::interval,
    NOW()
FROM generate_series(1, :N_COMMENTS_ROOT) AS i;

\echo '→ Seeding reply comments...'

INSERT INTO therapy_post_comments (
    post_id, author_id, parent_comment_id, content,
    created_at, updated_at
)
SELECT
    parent.post_id,
    1 + floor(random() * :N_USERS)::int,
    parent.id,
    '테스트 대댓글 본문 ' || parent.id || '-' || g,
    parent.created_at + interval '1 hour',
    NOW()
FROM (
    SELECT id, post_id, created_at
    FROM therapy_post_comments
    WHERE parent_comment_id IS NULL
    ORDER BY random()
    LIMIT :N_COMMENTS_REPLY
) AS parent
CROSS JOIN generate_series(1, 1) AS g;

\echo '→ Seeding reactions (ON CONFLICT DO NOTHING removes dupes)...'

INSERT INTO therapy_post_reactions (
    post_id, user_id, reaction_type, created_at, updated_at
)
SELECT
    1 + floor(random() * :N_POSTS)::int,
    1 + floor(random() * :N_USERS)::int,
    CASE
        WHEN random() < 0.60 THEN 'LIKE'
        WHEN random() < 0.85 THEN 'CURIOUS'
        ELSE 'USEFUL'
    END,
    NOW() - ((random() * 180 * 24 * 3600)::int || ' seconds')::interval,
    NOW()
FROM generate_series(1, :N_REACTIONS_TRY) AS i
ON CONFLICT (post_id, user_id) DO NOTHING;

\echo '→ Seeding scraps...'

INSERT INTO therapy_post_scraps (
    post_id, user_id, created_at, updated_at
)
SELECT
    1 + floor(random() * :N_POSTS)::int,
    1 + floor(random() * :N_USERS)::int,
    NOW() - ((random() * 180 * 24 * 3600)::int || ' seconds')::interval,
    NOW()
FROM generate_series(1, :N_SCRAPS_TRY) AS i
ON CONFLICT (post_id, user_id) DO NOTHING;

\echo '→ Backfilling popularity_score...'

UPDATE therapy_posts p
SET popularity_score = (
    COALESCE((SELECT COUNT(*) FROM therapy_post_reactions r WHERE r.post_id = p.id), 0) * 30
    + COALESCE((SELECT COUNT(*) FROM therapy_post_scraps s WHERE s.post_id = p.id), 0) * 20
    + CAST(EXTRACT(EPOCH FROM p.created_at) / 8640 AS BIGINT)
);

\echo '→ ANALYZE...'

ANALYZE users;
ANALYZE therapy_posts;
ANALYZE therapy_post_comments;
ANALYZE therapy_post_reactions;
ANALYZE therapy_post_scraps;

\echo ''
\echo '=== Seed complete ==='

SELECT 'users'                    AS table_name, COUNT(*) AS rows FROM users
UNION ALL SELECT 'therapy_posts',             COUNT(*) FROM therapy_posts
UNION ALL SELECT 'therapy_post_comments',     COUNT(*) FROM therapy_post_comments
UNION ALL SELECT 'therapy_post_reactions',    COUNT(*) FROM therapy_post_reactions
UNION ALL SELECT 'therapy_post_scraps',       COUNT(*) FROM therapy_post_scraps;
