# 검색 성능 벤치마크 가이드

긴 본문(~1000자) `LIKE` Full Scan vs 반정규화 `search_text`(100자) GIN Trigram 검색 성능 비교.

---

## 사전 준비

### 1. PostgreSQL 접속

```bash
docker-compose up -d db
docker exec -it builders-db psql -U builders -d builders
```

### 2. pg_trgm 확장 확인

```sql
SELECT * FROM pg_extension WHERE extname = 'pg_trgm';
```

### 3. 기존 더미 데이터 삭제

이전 테스트 데이터가 있으면 삭제:

```sql
DELETE FROM therapy_posts WHERE title LIKE '테스트 게시글%' OR title LIKE '일반 게시글%';
```

### 4. 더미 데이터 삽입 (긴 본문)

content ~1000자, search_text 100자로 반정규화 효과를 명확히 비교.
"감각통합" 매칭 1만건 + 비매칭 9만건 = 총 10만건 (매칭 비율 ~10%).

```sql
-- 유저 ID 확인
SELECT id FROM users LIMIT 5;

-- "감각통합" 매칭 게시글 1만건 (본문 ~1000자)
INSERT INTO therapy_posts (title, content, therapy_area, age_group, search_text, visibility, post_type, view_count, popularity_score, author_id, created_at, updated_at)
SELECT
    '테스트 게시글 ' || i,
    REPEAT('감각통합 치료는 아동의 감각 처리 능력을 향상시키는 데 중점을 둡니다. 언어치료와 작업치료를 병행하면 더 좋은 효과를 기대할 수 있습니다. 놀이치료 기법을 활용한 사례를 공유합니다. 치료사로서 임상 경험을 나누고 동료들과 소통하는 것이 중요합니다. ', 5) || '번호: ' || i,
    (ARRAY['SENSORY_INTEGRATION','SPEECH','OCCUPATIONAL','COGNITIVE','PHYSICAL','ART','MUSIC','PLAY','BEHAVIOR'])[1 + (i % 9)],
    (ARRAY['AGE_0_2','AGE_3_5','AGE_6_12','AGE_13_18'])[1 + (i % 4)],
    LOWER(LEFT('감각통합 치료는 아동의 감각 처리 능력을 향상시키는 데 중점을 둡니다. 언어치료와 작업치료를 병행하면 더 좋은 효과를 기대할 수 있습니다.', 100)
        || ' ' || (ARRAY['감각통합','언어치료','작업치료','인지치료','물리치료','미술치료','음악치료','놀이치료','행동치료'])[1 + (i % 9)]),
    'PUBLIC',
    'COMMUNITY',
    0,
    CAST(EXTRACT(EPOCH FROM NOW()) / 8640 AS BIGINT),
    1,
    NOW() - (i || ' minutes')::INTERVAL,
    NOW() - (i || ' minutes')::INTERVAL
FROM generate_series(1, 10000) AS i;

-- 비매칭 게시글 9만건 (본문 ~1000자)
INSERT INTO therapy_posts (title, content, therapy_area, age_group, search_text, visibility, post_type, view_count, popularity_score, author_id, created_at, updated_at)
SELECT
    '일반 게시글 ' || i,
    REPEAT('오늘 날씨가 좋아서 산책을 다녀왔습니다. 아이들과 함께 공원에서 시간을 보냈어요. 봄이 오면서 꽃이 피기 시작했고 바람도 따뜻해졌습니다. 주말에는 가족들과 함께 나들이를 계획하고 있습니다. 건강한 하루를 보내시길 바랍니다. ', 5) || '번호: ' || i,
    (ARRAY['SPEECH','OCCUPATIONAL','COGNITIVE','PHYSICAL','ART','MUSIC','PLAY','BEHAVIOR'])[1 + (i % 8)],
    (ARRAY['AGE_0_2','AGE_3_5','AGE_6_12','AGE_13_18'])[1 + (i % 4)],
    LOWER('오늘 날씨가 좋아서 산책을 다녀왔습니다 아이들과 함께 공원에서 시간을 보냈어요'
        || ' ' || (ARRAY['언어치료','작업치료','인지치료','물리치료','미술치료','음악치료','놀이치료','행동치료'])[1 + (i % 8)]),
    'PUBLIC',
    'COMMUNITY',
    0,
    CAST(EXTRACT(EPOCH FROM NOW()) / 8640 AS BIGINT),
    1,
    NOW() - (i || ' minutes')::INTERVAL,
    NOW() - (i || ' minutes')::INTERVAL
FROM generate_series(1, 90000) AS i;
```

### 5. 데이터 확인

```sql
SELECT COUNT(*) FROM therapy_posts WHERE deleted_at IS NULL;
-- 약 100,012건 예상
```

---

## 벤치마크 A: LIKE (긴 본문 Full Scan) vs GIN 인덱스 (반정규화 search_text)

정렬/LIMIT 영향 없이 순수하게 "content Full Scan vs search_text GIN 인덱스" 검색 성능만 비교.
COUNT(*)로 전체 매칭 건수를 세는 방식.

### A. LIKE on content (Before — 본문 전체 Seq Scan)

content(~1000자)에 LIKE 검색. 인덱스 비활성화로 Seq Scan 강제.

```sql
SET enable_indexscan = off;
SET enable_bitmapscan = off;

EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT COUNT(*)
FROM therapy_posts p
WHERE p.deleted_at IS NULL
  AND p.content LIKE '%감각통합%';

SET enable_indexscan = on;
SET enable_bitmapscan = on;
```

**확인 포인트**: `Seq Scan` + Execution Time 기록.

### B. GIN 인덱스 on search_text (반정규화 100자 + GIN 인덱스, similarity 없이)

search_text(100자)에 GIN 인덱스 검색. word_similarity() 없이 순수 GIN 인덱스 성능만 측정.

```sql
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT COUNT(*)
FROM therapy_posts p
WHERE p.deleted_at IS NULL
  AND p.search_text ILIKE '%감각통합%';
```

**확인 포인트**: `Bitmap Index Scan on idx_therapy_posts_search_text_trgm` + Execution Time 기록.

### C. GIN 인덱스 + word_similarity (관련도 정렬 포함)

GIN 인덱스로 후보를 추린 뒤 word_similarity()로 유사도 점수를 매겨 관련도 순 정렬.
실제 애플리케이션에서 사용하는 쿼리.

```sql
BEGIN;
SET LOCAL pg_trgm.word_similarity_threshold = 0.1;

EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT p.id, CAST(word_similarity('감각통합', p.search_text) AS numeric(10,8)) AS score
FROM therapy_posts p
WHERE p.deleted_at IS NULL
  AND (
        '감각통합' <% p.search_text
     OR p.search_text ILIKE '%' || '감각통합' || '%'
  )
ORDER BY score DESC, p.id DESC
LIMIT 11;

COMMIT;
```

**확인 포인트**: `Bitmap Index Scan` + word_similarity 계산 비용 확인.

### 결과 기록

| 항목 | LIKE on content (A) | GIN only (B) | GIN + similarity (C) |
|------|---------------------|--------------|----------------------|
| 대상 컬럼 | `content` (~1000자) | `search_text` (100자) | `search_text` (100자) |
| Scan 방식 | `Seq Scan` | `Bitmap Index Scan` | `Bitmap Index Scan` |
| Execution Time | _____ ms | _____ ms | _____ ms |
| Buffers | 기록 | 기록 | 기록 |

- A→B: 반정규화 + GIN 인덱스 도입 효과 (순수 성능)
- B→C: word_similarity() 관련도 정렬 추가 비용

**3회 반복 평균** 사용.

---

## 벤치마크 B: 2단계 Fetch vs 1회 JOIN

매칭 건수가 많을수록 1회 JOIN은 정렬 시 불필요한 본문(~1000자)을 메모리에 올리지만,
2단계 Fetch는 본문 조회가 항상 11건으로 고정.

### C. 1회 JOIN (본문 + 작성자 포함 정렬)

```sql
BEGIN;
SET LOCAL pg_trgm.word_similarity_threshold = 0.1;

EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT p.id, p.content, u.nickname,
       CAST(word_similarity('감각통합', p.search_text) AS numeric(10,8)) AS score
FROM therapy_posts p
JOIN users u ON p.author_id = u.id
WHERE p.deleted_at IS NULL
  AND (
        '감각통합' <% p.search_text
     OR p.search_text ILIKE '%' || '감각통합' || '%'
  )
ORDER BY score DESC, p.id DESC
LIMIT 11;

COMMIT;
```

### D. 2단계 Fetch

```sql
-- 0단계: 1단계에서 사용할 ID 목록 확인 (EXPLAIN 없이 실행)
BEGIN;
SET LOCAL pg_trgm.word_similarity_threshold = 0.1;

SELECT p.id
FROM therapy_posts p
WHERE p.deleted_at IS NULL
  AND (
        '감각통합' <% p.search_text
     OR p.search_text ILIKE '%' || '감각통합' || '%'
  )
ORDER BY (word_similarity('감각통합', p.search_text))::numeric(10,8) DESC, p.id DESC
LIMIT 11;

COMMIT;
-- 여기서 나온 11개 ID를 메모해둔다.

-- 1단계: (id, score)만 조회
BEGIN;
SET LOCAL pg_trgm.word_similarity_threshold = 0.1;

EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT p.id, CAST(word_similarity('감각통합', p.search_text) AS numeric(10,8)) AS score
FROM therapy_posts p
WHERE p.deleted_at IS NULL
  AND (
        '감각통합' <% p.search_text
     OR p.search_text ILIKE '%' || '감각통합' || '%'
  )
ORDER BY score DESC, p.id DESC
LIMIT 11;

COMMIT;

-- 2단계: 0단계에서 메모한 ID로 본문 + 작성자 조회
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT p.*, u.*
FROM therapy_posts p
JOIN users u ON p.author_id = u.id
WHERE p.id IN (110012,110011,110010,110009,110008,110007,110006,110005,110004,110003,110002);
```

### 결과 비교

| 항목 | 1회 JOIN (C) | 2단계 Fetch (D 합산) |
|------|-------------|---------------------|
| Execution Time | _____ ms | 1단계 + 2단계 = _____ ms |
| Buffers shared hit/read | 기록 | 1단계 + 2단계 합산 |

**핵심**: 매칭 건수가 많을수록 1회 JOIN은 정렬 시 불필요한 본문(~1000자)을 메모리에 올리는 비용이 증가하지만,
2단계 Fetch는 본문 조회가 항상 11건으로 고정.

---

## 테스트 후 정리

```sql
DELETE FROM therapy_posts WHERE title LIKE '테스트 게시글%' OR title LIKE '일반 게시글%';
```

---

## 주의사항

- `EXPLAIN ANALYZE`는 **실제로 쿼리를 실행**함. 운영 DB에서는 주의.
- 첫 실행은 cold cache이므로, **3회 반복 후 평균** 사용.
- `SET LOCAL`은 현재 트랜잭션에서만 유효. 반드시 `BEGIN; ... COMMIT;` 블록 안에서 실행.
