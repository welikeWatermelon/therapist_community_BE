# Commit 5: word_similarity(<%) 전환 + threshold 조정

## 무엇을 했나

relevance 검색 쿼리 4개에서:
- `similarity()` → `word_similarity()` 스코어링 함수 변경
- `%` → `<%` 매칭 연산자 변경
- `similarity_threshold = 0.03` → `word_similarity_threshold = 0.1` 임계값 상향

## 왜 했나

### 기존 `%` (similarity) 연산자의 문제

`similarity(A, B)` = 두 문자열 전체의 trigram 집합을 비교하여 0~1 사이 값 반환.

```
search_text = "감각통합 아동을 위한 놀이치료 사례 연구 감각통합 3세 5세"
keyword     = "감각통합"

similarity("감각통합", search_text) = 약 0.08
```

**문제:** search_text가 길수록 keyword의 trigram이 차지하는 비율이 낮아짐 → 점수 극도로 낮음.
그래서 threshold를 0.03까지 낮춰야 매칭이 됐음 → **거의 모든 문서가 통과** → noise 증가.

### 새로운 `<%` (word_similarity) 연산자

`word_similarity(keyword, text)` = keyword가 text 내 **연속 부분 문자열**과 얼마나 유사한지 평가.

```
word_similarity("감각통합", search_text) = 약 0.75
```

**핵심 차이:**
- `similarity`: 전체 vs 전체 비교
- `word_similarity`: keyword vs text의 가장 유사한 부분 비교

→ 짧은 keyword + 긴 search_text 조합에서 **훨씬 높은 점수** → threshold를 정상 범위(0.1)로 올릴 수 있음.

## 핵심 개념 학습

### 1. `<%` 연산자의 방향성

```sql
-- 올바른 방향: keyword가 search_text의 부분 문자열과 유사한가?
:keyword <% p.search_text

-- 동일한 의미 (역방향 연산자)
p.search_text %> :keyword
```

**`<%`는 비대칭 연산자:**
- `A <% B` = "A가 B의 어떤 연속 부분과 유사한가?"
- `B <% A`는 다른 의미 → 방향을 틀리면 결과가 완전히 달라짐

### 2. word_similarity() 함수의 인자 순서

```sql
-- word_similarity(keyword, text) — keyword가 첫 번째
CAST(word_similarity(:keyword, p.search_text) AS numeric(10,8)) AS score
```

`word_similarity(a, b)`: a를 b 내에서 찾아 가장 높은 유사도 반환.
`similarity(a, b)`: 순서 무관 (대칭).

### 3. threshold의 역할

```sql
SET LOCAL pg_trgm.word_similarity_threshold = 0.1;
```

**`<%` 연산자는 `word_similarity_threshold`를 참조** (`similarity_threshold`가 아님!)

| 설정 | 연산자 | 함수 |
|------|--------|------|
| `pg_trgm.similarity_threshold` | `%` | `similarity()` |
| `pg_trgm.word_similarity_threshold` | `<%` | `word_similarity()` |
| `pg_trgm.strict_word_similarity_threshold` | `<<%` | `strict_word_similarity()` |

### 4. GIN 인덱스와 `<%` 연산자

`gin_trgm_ops`는 `<%` 연산자도 지원함.
PostgreSQL이 자동으로 keyword에서 trigram을 추출 → GIN 인덱스에서 후보 행을 필터.

```
EXPLAIN ANALYZE
SELECT ... WHERE :keyword <% p.search_text ...

→ Bitmap Index Scan on idx_therapy_posts_search_text_trgm
```

### 5. threshold 0.03 → 0.1 변경 근거

| threshold | `%` (similarity) | `<%` (word_similarity) |
|-----------|-------------------|----------------------|
| 0.03 | 거의 모든 문서 통과 | 필요 없을 정도로 관대 |
| 0.1 | 여전히 많은 noise | **적절한 필터링** |
| 0.3 (PG 기본) | 한글에서 너무 엄격 | 한글에서도 적절할 수 있음 |

0.1은 보수적 시작점. 로그 데이터를 보고 0.15~0.2로 올릴 수 있음.
ILIKE fallback이 있으므로 `<%`가 놓치는 정확한 서브스트링 매칭은 여전히 커버됨.

## 변경된 쿼리 비교

### Before
```sql
SELECT p.id, CAST(similarity(p.search_text, :keyword) AS numeric(10,8)) AS score
FROM therapy_posts p
WHERE ...
  AND (p.search_text % :keyword OR p.search_text ILIKE ...)
```

### After
```sql
SELECT p.id, CAST(word_similarity(:keyword, p.search_text) AS numeric(10,8)) AS score
FROM therapy_posts p
WHERE ...
  AND (:keyword <% p.search_text OR p.search_text ILIKE ...)
```

## 수정 파일

| 파일 | 변경 |
|------|------|
| `TherapyPostRepository.java` | 4개 네이티브 쿼리: `%`→`<%`, `similarity()`→`word_similarity()` |
| `PostService.java` | `similarity_threshold = 0.03` → `word_similarity_threshold = 0.1` |
