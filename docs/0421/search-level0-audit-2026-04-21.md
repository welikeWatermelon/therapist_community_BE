# 작업 요청: trigram + GIN 검색 구현 품질 감사

## 목표
현재 MelloMe의 검색 기능(trigram + GIN 기반)이 **최적화된 상태**인지 감사한다.
단순히 "동작하는가"가 아니라 "운영 환경에서 성능/품질/안정성을 보장하는가"를 기준으로 점검한다.

## 이 작업에서 네가 해야 할 것

**이번 턴에서는 코드를 수정하지 마라.**
읽기 + 분석 + 문제점 리포트 + 개선 제안만 수행한다.
실제 수정은 별도 승인 후 진행한다.

## 감사 범위

### Section 1: 인덱스 건강도

다음을 코드와 마이그레이션 파일에서 확인:

#### 1-1. 인덱스 정의 검증
- [ ] `gin_trgm_ops` 사용 여부 (꼭 이 연산자 클래스여야 함)
- [ ] 인덱스 대상 컬럼이 실제 검색 쿼리에서 쓰는 컬럼과 일치하는지
- [ ] 중복/불필요한 인덱스 여부 (예: V15 tsvector 인덱스 잔재)
- [ ] partial index 활용 가능성 (deleted_at IS NULL 등)
- [ ] CONCURRENTLY 옵션 사용 여부 (프로덕션 배포 시 락 방지)

#### 1-2. 인덱스 사용 여부 검증
- [ ] 주요 검색 쿼리에 대한 EXPLAIN 실행 예시 제시
- [ ] Seq Scan 대신 Bitmap Index Scan이 나와야 함
- [ ] 검색 쿼리가 인덱스를 타지 않을 수 있는 anti-pattern 탐지:
    - ILIKE와 % 혼용 시 인덱스 타는 조건 확인
    - LOWER() 등 함수 래핑으로 인덱스 무효화되는지
    - OR 조건이 인덱스를 깨뜨리는지

#### 1-3. 인덱스 크기 및 성능
- [ ] 예상 인덱스 크기 계산 (게시글 수 x 평균 trigram 수)
- [ ] maintenance_work_mem 설정 확인 (인덱스 빌드 성능)

### Section 2: 검색 쿼리 품질

#### 2-1. similarity_threshold 적정성
- [ ] 현재 값 `0.03`은 매우 낮음 — 근거가 있는지?
- [ ] 실제 검색 결과 noise/relevance 비율 측정 가능한지
- [ ] 검색어 길이에 따라 동적 조정 필요성 검토

#### 2-2. 연산자 선택 적절성
현재 `%`, `ILIKE`, `similarity()`를 혼용 중. 다음 검토:

- [ ] `%` vs `<%` (word_similarity) 비교
    - 짧은 검색어 + 긴 문서 케이스에서 `<%`가 더 적합할 수 있음
    - MelloMe search_text가 제목+내용 결합이라 길어서 `<%` 고려 가치 있음

- [ ] `ILIKE '%keyword%'`의 역할
    - trigram이 못 잡는 케이스 보완용인가?
    - 인덱스 활용되는지 확인 필요
    - 2글자 이하 검색어에서만 의미 있을 수 있음

- [ ] `similarity()` 스코어링과 필터링 조건의 정합성

#### 2-3. 검색어 전처리
- [ ] 공백 정규화 (중복 공백, 앞뒤 공백)
- [ ] 특수문자 처리 (%, _, ' 등 SQL injection 가능성)
- [ ] 대소문자 처리
- [ ] 너무 짧은 검색어 방어 (1글자)
- [ ] 너무 긴 검색어 방어 (DoS 방지)
- [ ] 이모지/유니코드 처리

#### 2-4. 한글 특화 처리
- [ ] 초성 검색 (titleChoseong)의 트리거 조건
    - 언제 초성 검색을 쓰고 언제 일반 검색을 쓰는가?
    - 혼용 시 점수 체계가 일관적인가?
- [ ] 자음/모음만 입력 시 처리
- [ ] 숫자+한글 혼합 검색 ("30대 우울" 등)

### Section 3: 페이지네이션 건강도

#### 3-1. 커서 안정성
- [ ] `(lastScore, lastId)` 조합이 정렬 안정성 보장하는지
- [ ] 동일 score 건이 많을 때 누락/중복 없는지
- [ ] score가 null일 때 처리

#### 3-2. OFFSET 회피 확인
- [ ] 커서 방식만 쓰고 있는지
- [ ] 혹시 page number 방식 잔재가 있는지

#### 3-3. limit + 1 패턴
- [ ] hasNext 판단을 위한 limit + 1 fetch 쓰는지
- [ ] 또는 별도 count 쿼리 날리는지 (성능 낭비)

### Section 4: 데이터 정합성

#### 4-1. search_text 동기화
- [ ] 제목/내용 수정 시 search_text 재빌드되는가?
- [ ] `buildSearchText()` 호출 시점 전수 확인
    - create() ✅
    - update() ✅
    - 다른 수정 경로 (Admin, 소프트 삭제 등) 누락 여부

#### 4-2. 부분 데이터 처리
- [ ] title 없거나 content 없는 경우 search_text 값
- [ ] therapyArea/ageGroup 변경 시 search_text 갱신되는지
- [ ] 저장 전 search_text NULL 체크

#### 4-3. 백필 필요성
- [ ] V25 마이그레이션의 백필 로직이 모든 기존 게시글을 커버했는지
- [ ] search_text가 비어있는 레코드 쿼리
```sql
  SELECT COUNT(*) FROM therapy_posts 
  WHERE search_text IS NULL OR search_text = '';
```

### Section 5: 성능/운영

#### 5-1. N+1 쿼리
- [ ] 검색 결과에 author 정보 조인하는 방식
- [ ] Lazy Loading 트랩 없는지
- [ ] @EntityGraph 또는 fetch join 사용 여부

#### 5-2. 응답 시간
- [ ] 검색 API의 평균/p95 응답 시간 측정 흔적
- [ ] 로그에 쿼리 실행 시간 기록되는지

#### 5-3. 캐싱
- [ ] 인기 검색어 캐싱 전략 (Redis 활용)
- [ ] 검색 결과 캐싱의 실용성 검토 (invalidation 어려움)

### Section 6: 테스트 커버리지

#### 6-1. 단위 테스트
- [ ] search_text 빌드 로직 테스트
- [ ] 검색어 전처리 테스트
- [ ] edge case (빈 키워드, 특수문자 등)

#### 6-2. 통합 테스트
- [ ] 실제 검색 API 호출 테스트
- [ ] 페이지네이션 연속 호출 테스트
- [ ] 정렬 안정성 테스트

#### 6-3. H2 vs PostgreSQL 이슈
- [ ] H2에서 pg_trgm이 정확히 재현되지 않을 수 있음
- [ ] trigram 관련 테스트가 실제로 검증하는 범위

### Section 7: 모니터링 가능성

#### 7-1. 검색 로그
- [ ] 검색 요청 로깅 여부 (keyword, 결과 건수, 응답 시간)
- [ ] zero-result 식별 가능한지
- [ ] 인기 검색어 집계 가능한지

#### 7-2. 에러 핸들링
- [ ] 검색 중 예외 발생 시 사용자 응답
- [ ] 쿼리 실패 로깅

## 평가 기준

각 체크 항목은 다음 중 하나로 평가:

- ✅ **OK**: 현재 구현이 적절함
- ⚠️ **개선 권장**: 동작하지만 더 나은 방법 있음
- 🔴 **즉시 수정**: 버그 또는 성능/보안 이슈

## 제약

- 코드 수정 금지 (읽기만)
- 가정보다 코드 실측 우선
- 확인 불가한 항목은 "확인 필요" + 확인 방법 제시
- 지나친 리팩토링 제안 지양 (실용적 개선 중심)
- PostgreSQL 16 + pg_trgm 공식 문서 기반 평가
- 과장된 심각도 부여 금지 (Critical은 진짜 급한 것만)

---
---

# 감사 결과

# trigram + GIN 검색 감사 리포트

## 1. 핵심 발견 (Executive Summary)

### 양호한 점 3가지
1. **인덱스 전략 건전** — V15 tsvector 잔재 깔끔하게 제거됨, `gin_trgm_ops` 올바르게 사용
2. **커서 페이지네이션 안정적** — `(score DESC, id DESC)` + `size+1` 패턴으로 누락/중복 방지
3. **부동소수 정밀도 처리 우수** — `numeric(10,8)` 캐스트 + BigDecimal로 round-trip 정밀도 보장

### 즉시 조치 필요 사항 Top 3
1. 🔴 **titleChoseong 필드 미사용(Dead Field)** — 컬럼+인덱스 존재하나 Java에서 한번도 populate하지 않음
2. 🔴 **keyword 길이 제한 없음** — 매우 긴 검색어로 DoS 가능 (similarity 계산 비용 증가)
3. 🔴 **title 필드가 API에 노출되지 않음** — searchText에 title 포함하지만 항상 null → 의미 없는 빈 문자열

### 중기 개선 사항 Top 3
1. ⚠️ `similarity_threshold = 0.03` 과도하게 낮음 → noise 증가 우려
2. ⚠️ V16의 `idx_therapy_posts_title_trgm`, `idx_therapy_posts_content_trgm` 인덱스가 현재 검색 쿼리에서 사용되지 않음 (V25의 search_text 인덱스만 사용)
3. ⚠️ H2 테스트 환경에서 pg_trgm 네이티브 쿼리 검증 불가

---

## 2. 섹션별 감사 결과

### 2.1 인덱스 건강도

#### 1-1. 인덱스 정의 검증

| 항목 | 평가 | 상세 |
|------|------|------|
| `gin_trgm_ops` 사용 | ✅ OK | V16, V25 모두 `gin_trgm_ops` 사용 |
| 인덱스-쿼리 컬럼 일치 | ⚠️ 개선 권장 | 실제 검색은 `search_text`만 사용 → V16의 `title`, `content` 개별 인덱스 불필요 |
| 중복/불필요 인덱스 | ⚠️ 개선 권장 | `idx_therapy_posts_title_trgm`, `idx_therapy_posts_content_trgm` 미사용 |
| partial index | ⚠️ 개선 권장 | `search_text` GIN 인덱스에 `WHERE deleted_at IS NULL` 없음 |
| CONCURRENTLY 옵션 | ⚠️ 개선 권장 | Flyway에서 미사용 (소규모 데이터라 현재 문제 없지만 향후 주의) |

**미사용 인덱스 목록:**
- `idx_therapy_posts_title_trgm` — 검색 쿼리가 `search_text` 컬럼만 참조
- `idx_therapy_posts_content_trgm` — 동일
- `idx_therapy_posts_title_choseong` — titleChoseong 자체가 미 populate

#### 1-2. 인덱스 사용 여부

**대표 쿼리 예상 실행 계획:**

```sql
-- relevance search (% operator)
EXPLAIN ANALYZE
SELECT p.id, CAST(similarity(p.search_text, '감각통합') AS numeric(10,8)) AS score
FROM therapy_posts p
WHERE p.deleted_at IS NULL
  AND (p.search_text % '감각통합' OR p.search_text ILIKE '%감각통합%')
ORDER BY score DESC, p.id DESC
LIMIT 11;
```

**예상 플랜:**
```
Limit (rows=11)
  -> Sort (score DESC, id DESC)
    -> Bitmap Heap Scan on therapy_posts
      Recheck Cond: ((search_text % '감각통합') OR (search_text ~~* '%감각통합%'))
      Filter: (deleted_at IS NULL)
      -> BitmapOr
        -> Bitmap Index Scan on idx_therapy_posts_search_text_trgm
        -> Bitmap Index Scan on idx_therapy_posts_search_text_trgm
```

✅ GIN 인덱스 활용됨. `deleted_at IS NULL` 필터는 Heap Scan 단계에서 처리 (partial index가 있으면 더 효율적).

**Anti-pattern 분석:**

| 패턴 | 평가 | 설명 |
|------|------|------|
| ILIKE + GIN trigram | ✅ OK | `gin_trgm_ops`는 ILIKE 패턴도 가속 |
| LOWER() 함수 래핑 | ✅ OK | relevance 쿼리에는 LOWER() 미사용 (ILIKE 자체가 case-insensitive) |
| OR 조건 | ✅ OK | BitmapOr로 양쪽 모두 인덱스 활용 가능 |
| JPQL searchByKeyword의 LOWER(CONCAT(...)) | ⚠️ | 이 쿼리는 trigram 인덱스 타지 않을 수 있음 (content 컬럼 대상 + LOWER 함수) |

#### 1-3. 인덱스 크기

- 현재 게시글 30개(시드) → 인덱스 크기 무시 가능 (수 KB)
- 10만 게시글 x 평균 search_text 200자 x trigram 수 ≈ 100~200MB GIN 인덱스 예상
- 현재 규모에서는 문제 없음

---

### 2.2 검색 쿼리 품질

#### 2-1. similarity_threshold 적정성

| 항목 | 평가 | 상세 |
|------|------|------|
| 현재 값 0.03 | ⚠️ 개선 권장 | PostgreSQL 기본값 0.3의 1/10 — 매우 관대한 설정 |
| 근거 | 확인 필요 | 한글 trigram 특성(자모 분해 없이 2~3글자 단위)상 낮을 수밖에 없으나, 0.03은 거의 모든 것이 매칭됨 |
| 동적 조정 | ⚠️ 개선 권장 | 검색어 길이에 따라 차등 적용 고려 (짧은 키워드 → 높은 threshold) |

**분석:**
- 한글은 영어보다 trigram 일치율이 낮음 (자모 조합 방식)
- 그러나 0.03이면 "감각"으로 검색 시 "통합"이 포함된 거의 모든 문서 반환
- ILIKE fallback이 있으므로 `%` 연산자의 threshold를 0.1~0.15로 올려도 누락 걱정 적음

#### 2-2. 연산자 선택 적절성

| 항목 | 평가 | 상세 |
|------|------|------|
| `%` vs `<%` | ⚠️ 개선 권장 | `search_text`가 길므로(title+content+area+age) `<%` (word_similarity)가 더 적합할 수 있음 |
| ILIKE의 역할 | ✅ OK | trigram이 못 잡는 2글자 이하 + 완전 서브스트링 매칭 보완 |
| similarity() 스코어링 | ✅ OK | 필터(`%`)와 스코어링(`similarity()`)이 분리되어 정합성 있음 |

**`%` vs `<%` 비교:**
- `%`: 전체 문자열 대 전체 문자열 유사도 → 긴 search_text에서 짧은 keyword의 유사도가 매우 낮아짐
- `<%`: 단어 단위 유사도 → "감각통합"이 "감각통합 언어치료 3세 5세 ..." 문자열 내에서 높은 점수
- **현재는 threshold 0.03으로 보상하고 있지만, `<%`로 전환하면 threshold를 정상 범위로 올릴 수 있음**

#### 2-3. 검색어 전처리

| 항목 | 평가 | 상세 |
|------|------|------|
| 공백 정규화 | ✅ OK | `.trim()` 적용 |
| 특수문자(ILIKE용) | ✅ OK | `\`, `%`, `_` 이스케이프 (PostSearchCondition) |
| 대소문자 | ✅ OK | ILIKE 자체가 case-insensitive, `%` 연산자는 소문자 변환 불필요 |
| 짧은 검색어 방어 | 🔴 즉시 수정 | **최소 길이 검증 없음** — 1글자 검색 가능 → trigram 생성 불가(3글자 미만) |
| 긴 검색어 방어 | 🔴 즉시 수정 | **최대 길이 검증 없음** — 수천 자 검색어 가능 → similarity() 계산 비용 |
| 이모지/유니코드 | ⚠️ 개선 권장 | 별도 처리 없음 (pg_trgm은 유니코드 처리 가능하나 이모지 trigram은 noise) |

#### 2-4. 한글 특화 처리

| 항목 | 평가 | 상세 |
|------|------|------|
| 초성 검색(titleChoseong) | 🔴 즉시 수정 | HangulUtils 존재하나 **실제 populate 코드 없음** — Dead feature |
| 자음/모음만 입력 | ⚠️ 개선 권장 | 별도 처리 없음 (ㄱ, ㅏ 등 단독 입력 시 trigram 매칭 불가) |
| 숫자+한글 혼합 | ✅ OK | "30대 우울" 같은 쿼리는 ILIKE fallback으로 커버 |

---

### 2.3 페이지네이션 건강도

| 항목 | 평가 | 상세 |
|------|------|------|
| (lastScore, lastId) 안정성 | ✅ OK | score DESC + id DESC — id는 unique이므로 동점 시에도 결정적 |
| 동일 score 다수 | ✅ OK | `score = :lastScore AND id < :lastId`로 정확히 다음 위치 |
| score null 처리 | ✅ OK | `similarity()`는 항상 값 반환 (0.0~1.0), null 불가 |
| OFFSET 회피 | ✅ OK | 전부 커서 방식, page number 잔재 없음 (relevance 검색) |
| limit + 1 패턴 | ✅ OK | `size + 1` 조회 후 trim — 별도 count 쿼리 없음 |

**단, JPQL 기반 `searchByKeyword`는 offset 페이지네이션(`PageRequest.of(page, size)`) 사용 중** — 이건 `/api/v1/posts` 엔드포인트이므로 별도 이슈.

---

### 2.4 데이터 정합성

| 항목 | 평가 | 상세 |
|------|------|------|
| create() 시 searchText 빌드 | ✅ OK | 생성자에서 `buildSearchText()` 호출 |
| update() 시 searchText 빌드 | ✅ OK | `update()` 메서드에서 재빌드 |
| Admin 수정 경로 | ✅ OK | 별도 Admin 직접 수정 경로 없음 |
| title 없는 경우 | ⚠️ 개선 권장 | title이 항상 null → searchText에 빈 문자열 기여 (무해하지만 불필요) |
| therapyArea 변경 시 | ✅ OK | `update()`에서 재빌드됨 |
| ageGroup 변경 시 | ⚠️ 개선 권장 | ageGroup 변경 API가 없어 현재 문제 없으나 향후 주의 |
| V25 백필 | ✅ OK | `WHERE search_text IS NULL` 조건으로 전체 커버 |

---

### 2.5 성능/운영

| 항목 | 평가 | 상세 |
|------|------|------|
| N+1 쿼리 | ✅ OK | 2-stage fetch (ID 조회 → `findAllByIdInWithAuthor` EntityGraph) |
| 응답 시간 측정 | ⚠️ 개선 권장 | 검색 전용 로그/메트릭 없음 |
| 캐싱 | ⚠️ 개선 권장 | 검색 결과 캐싱 없음 (현재 규모에선 불필요) |

---

### 2.6 테스트 커버리지

| 항목 | 평가 | 상세 |
|------|------|------|
| searchText 빌드 로직 | 🔴 즉시 수정 | **테스트 없음** |
| 검색어 전처리 | ⚠️ 개선 권장 | PostSearchCondition escape 테스트 없음 |
| edge case (빈 키워드, 특수문자) | ⚠️ 개선 권장 | 미확인 |
| API 통합 테스트 | ⚠️ 개선 권장 | 컨트롤러 테스트 존재하나 mock 기반 |
| H2 vs PostgreSQL | 🔴 즉시 수정 | H2에서 `%` 연산자, `similarity()` 불가 → 네이티브 쿼리 미검증 |

---

### 2.7 모니터링 가능성

| 항목 | 평가 | 상세 |
|------|------|------|
| 검색 요청 로깅 | 🔴 즉시 수정 | 없음 — zero-result, 인기 검색어 식별 불가 |
| 에러 핸들링 | ✅ OK | GlobalExceptionHandler가 처리 |

---

## 3. 실제 EXPLAIN 결과

실행 환경 접근 불가로 **예상 플랜** 제시:

### 쿼리 1: relevance 검색 (% + ILIKE)

```sql
EXPLAIN ANALYZE
SELECT p.id, CAST(similarity(p.search_text, '언어치료') AS numeric(10,8)) AS score
FROM therapy_posts p
WHERE p.deleted_at IS NULL
  AND (p.search_text % '언어치료' OR p.search_text ILIKE '%언어치료%')
ORDER BY score DESC, p.id DESC LIMIT 11;
```

**예상:**
```
Limit (rows=11)
  -> Sort (score DESC, id DESC)
    -> Bitmap Heap Scan on therapy_posts
      Recheck Cond: ((search_text % '언어치료') OR (search_text ~~* '%언어치료%'))
      Filter: (deleted_at IS NULL)
      -> BitmapOr
        -> Bitmap Index Scan on idx_therapy_posts_search_text_trgm
        -> Bitmap Index Scan on idx_therapy_posts_search_text_trgm
```

✅ GIN 인덱스 활용됨. `deleted_at IS NULL` 필터는 Heap Scan 단계에서 처리 (partial index가 있으면 더 효율적).

### 쿼리 2: JPQL searchByKeyword (LOWER + LIKE)

```sql
EXPLAIN ANALYZE
SELECT * FROM therapy_posts p
WHERE p.deleted_at IS NULL
  AND LOWER(p.content) LIKE LOWER('%언어%');
```

**예상:**
```
Seq Scan on therapy_posts
  Filter: (deleted_at IS NULL AND lower(content) ~~ lower('%언어%'))
```

⚠️ **`idx_therapy_posts_content_trgm`이 있지만 `LOWER()` 래핑으로 인덱스 활용 불확실.** PostgreSQL은 trigram GIN 인덱스로 `ILIKE`는 가속하지만 `LOWER() + LIKE` 조합은 다를 수 있음.

**확인 방법:** 실제 `EXPLAIN` 실행 필요. 만약 Seq Scan이면 `ILIKE`로 변경 권장.

---

## 4. 발견된 이슈 목록

### Issue #1: 검색어 길이 제한 없음
- **심각도:** 🔴 Critical
- **위치:** `PostController.java:113` (keyword 파라미터)
- **현재 코드:**
  ```java
  @RequestParam String keyword,
  ```
- **문제점:** 수천 자 검색어 입력 시 `similarity()` 계산 비용 급증 + GIN 스캔 과부하
- **개선 제안:**
  ```java
  @RequestParam @Size(min = 2, max = 100) String keyword,
  ```
- **예상 작업 시간:** 10분

### Issue #2: titleChoseong Dead Field
- **심각도:** 🔴 High
- **위치:** `TherapyPost.java:51-52`, `V16 마이그레이션`
- **현재 코드:** 컬럼 + 인덱스 존재, populate 코드 없음
- **문제점:** 불필요한 인덱스 유지 비용 + 혼란
- **개선 제안:** (A) 초성 검색 구현 또는 (B) 컬럼/인덱스 제거 마이그레이션
- **예상 작업 시간:** 구현 시 0.5일 / 제거 시 10분

### Issue #3: 미사용 인덱스 3개
- **심각도:** ⚠️ Medium
- **위치:** V16 마이그레이션
- **인덱스:** `idx_therapy_posts_title_trgm`, `idx_therapy_posts_content_trgm`, `idx_therapy_posts_title_choseong`
- **문제점:** 쓰기 시 인덱스 갱신 비용 낭비 (현재 규모에선 미미)
- **개선 제안:** 새 마이그레이션으로 DROP INDEX
- **예상 작업 시간:** 15분

### Issue #4: searchText 테스트 부재
- **심각도:** ⚠️ Medium
- **위치:** `src/test/` — 관련 테스트 없음
- **문제점:** buildSearchText 로직 변경 시 regression 감지 불가
- **개선 제안:** TherapyPostTest에 searchText 빌드 단위테스트 추가
- **예상 작업 시간:** 30분

### Issue #5: similarity_threshold 0.03 과도하게 낮음
- **심각도:** ⚠️ Medium
- **위치:** `PostService.java:149`
- **현재 코드:**
  ```java
  entityManager.createNativeQuery("SET LOCAL pg_trgm.similarity_threshold = 0.03")
  ```
- **문제점:** 거의 모든 문서가 `%` 연산자를 통과 → ILIKE와 사실상 중복
- **개선 제안:** 0.1로 올리고 ILIKE fallback에 의존 / 또는 `<%` 전환
- **예상 작업 시간:** 테스트 포함 2시간

### Issue #6: JPQL searchByKeyword의 LOWER()+LIKE 패턴
- **심각도:** ⚠️ Medium
- **위치:** `TherapyPostRepository.java:38`
- **현재 코드:**
  ```java
  AND LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
  ```
- **문제점:** LOWER() 래핑이 GIN trigram 인덱스 활용을 방해할 수 있음
- **개선 제안:** `content ILIKE CONCAT('%', :keyword, '%')` 또는 relevance 엔드포인트로 통합
- **예상 작업 시간:** 30분 (EXPLAIN 검증 포함)

---

## 5. 개선 로드맵

### Phase 1 (즉시, 1일 이내): 버그성 이슈
1. keyword 길이 제한 추가 (`@Size(min=2, max=100)`)
2. size 파라미터 범위 제한 (max 50)

### Phase 2 (단기, 1주 이내): 최적화
1. 미사용 인덱스 DROP 마이그레이션
2. titleChoseong 처리 결정 (구현 or 제거)
3. similarity_threshold 조정 (0.03 → 0.1) 또는 `<%` 전환
4. JPQL `LOWER()+LIKE` → `ILIKE` 변경

### Phase 3 (중기, 1달 이내): 모니터링/테스트
1. searchText 빌드 단위테스트 추가
2. 검색 요청 로깅 (keyword, 결과 수, 응답시간)
3. Testcontainers 도입 또는 PostgreSQL 전용 테스트 프로필
4. 검색 품질 벤치마크 (대표 쿼리 결과 스냅샷)

---

## 6. 벤치마크 제안

pgvector로 가기 전 측정해둘 지표:

| 지표 | 측정 방법 | 목적 |
|------|----------|------|
| 평균 응답 시간 | 대표 검색어 10개 x 3회 반복 | 하이브리드 전환 후 비교 기준 |
| EXPLAIN 플랜 | `EXPLAIN (ANALYZE, BUFFERS)` | 인덱스 활용 확인 |
| 인덱스 크기 | `pg_relation_size('idx_therapy_posts_search_text_trgm')` | 성장 추이 |
| zero-result 비율 | 로깅 추가 후 1주간 수집 | 현재 검색 품질 정량화 |

**대표 쿼리 10개 제안:**
1. "감각통합" (정확 매칭)
2. "감각" (부분 매칭, 2글자)
3. "언어치료 3세" (복합 키워드)
4. "ㄱㅅ" (초성 — 현재 미지원 확인)
5. "occupational" (영문)
6. "우울 불안" (띄어쓰기 포함)
7. "a" (1글자 — edge case)
8. "놀이치료감각통합인지치료" (띄어쓰기 없는 긴 문자열)
9. "%" (특수문자)
10. "미술" (짧은 한글, 여러 결과 예상)
