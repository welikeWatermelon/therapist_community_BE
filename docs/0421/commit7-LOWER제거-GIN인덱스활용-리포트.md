# Commit 7: JPQL LOWER() 제거 — GIN trigram 인덱스 활용 보장

## 무엇을 했나

JPQL 키워드 검색 쿼리 2개에서 `LOWER()` 래핑을 제거했다:
```java
// Before
AND LOWER(p.searchText) LIKE LOWER(CONCAT('%', :keyword, '%'))

// After
AND p.searchText LIKE CONCAT('%', :keyword, '%')
```

## 왜 했나

### 검증 결과: LOWER()+LIKE는 GIN 인덱스를 타지 않는다

PostgreSQL 공식 문서에 따르면, GIN trigram 인덱스(`gin_trgm_ops`)가 가속하는 패턴:
- `column LIKE '%pattern%'` ✅
- `column ILIKE '%pattern%'` ✅
- `column % 'pattern'` ✅
- `column <% 'pattern'` ✅
- **`LOWER(column) LIKE LOWER('%pattern%')` ❌** — 함수 래핑 시 인덱스 무효화

**왜 무효화되는가:**
```
인덱스: search_text 컬럼에 대해 생성됨
쿼리:  LOWER(search_text) 에 대해 검색
```
PostgreSQL 플래너는 `search_text`와 `LOWER(search_text)`를 **다른 표현식**으로 인식.
인덱스가 `search_text`에 걸려있으므로 `LOWER(search_text)`에는 적용 불가 → Seq Scan.

### LOWER() 제거해도 괜찮은 이유

**이 서비스는 한글 위주:**
- 검색어: "감각통합", "언어치료", "놀이치료" 등 → 대소문자 개념 없음
- `search_text` 내용: title + content(한글) + therapyArea("감각통합") + ageGroup("3세 5세")
- 영문이 들어올 가능성: 극히 낮음 ("ADHD" 정도)

**영문 edge case 커버:**
- `/api/v1/posts/search` (relevance 검색)의 ILIKE가 case-insensitive로 커버
- 두 엔드포인트가 공존하므로 `/api/v1/posts`의 bare LIKE로 못 잡는 건 `/search`로 가능

### 대안으로 검토했지만 선택하지 않은 것들

| 대안 | 장점 | 단점 | 결정 |
|------|------|------|------|
| 네이티브 SQL + ILIKE | 인덱스 활용 + 대소문자 무시 | `@EntityGraph` 사용 불가, `Page` 반환 구조 변경 | ❌ 과도한 변경 |
| `LOWER(search_text)` 함수형 인덱스 추가 | LOWER()+LIKE에서 인덱스 활용 | 인덱스 하나 더 유지, 용도 제한적 | ❌ 불필요한 인덱스 |
| **bare LIKE (LOWER 제거)** | 기존 GIN 인덱스 그대로 활용, 구조 변경 없음 | 영문 대소문자 구분됨 | ✅ 채택 |

## 핵심 개념 학습

### 1. PostgreSQL GIN trigram 인덱스가 지원하는 연산자/패턴

공식 문서 기준:

```
지원 O:
  column LIKE '%pattern%'       -- bare LIKE
  column ILIKE '%pattern%'      -- case-insensitive LIKE
  column ~ 'regex'              -- 정규식
  column ~* 'regex'             -- case-insensitive 정규식
  column % 'text'               -- similarity
  column <% 'text'              -- word_similarity

지원 X:
  LOWER(column) LIKE ...        -- 함수 래핑
  UPPER(column) LIKE ...        -- 함수 래핑
  SUBSTRING(column, ...) LIKE ...  -- 함수 래핑
```

**원칙:** GIN 인덱스는 **컬럼 그대로**에 대해서만 동작. 함수를 씌우면 다른 표현식.

### 2. 함수형 인덱스 (Expression Index)

만약 `LOWER()`가 꼭 필요했다면:
```sql
CREATE INDEX idx_search_text_lower_trgm 
    ON therapy_posts USING GIN (LOWER(search_text) gin_trgm_ops);
```

이러면 `LOWER(search_text) LIKE ...` 쿼리가 이 인덱스를 탈 수 있음.
하지만 인덱스 2개 유지(원본 + LOWER)는 쓰기 비용 증가 → 불필요하므로 선택하지 않음.

### 3. LIKE vs ILIKE (PostgreSQL)

| 연산자 | 대소문자 | SQL 표준 | JPQL 지원 | GIN 인덱스 |
|--------|---------|---------|----------|-----------|
| `LIKE` | 구분함 | ✅ 표준 | ✅ | ✅ |
| `ILIKE` | 무시함 | ❌ PG 전용 | ❌ | ✅ |

JPQL에서 ILIKE를 쓰려면 **네이티브 쿼리**로 전환해야 함.
네이티브로 가면 `@EntityGraph`, `Page<T>` 반환 등 JPA 편의 기능을 잃음.

### 4. 쿼리 실행 계획 확인 방법

배포 후 이 쿼리로 확인:
```sql
-- LOWER() 있을 때 (Seq Scan 예상)
EXPLAIN ANALYZE
SELECT * FROM therapy_posts
WHERE LOWER(search_text) LIKE LOWER('%감각통합%');

-- LOWER() 없을 때 (Bitmap Index Scan 예상)  
EXPLAIN ANALYZE
SELECT * FROM therapy_posts
WHERE search_text LIKE '%감각통합%';
```

## 수정 파일

| 파일 | 변경 |
|------|------|
| `TherapyPostRepository.java` | `searchByKeyword`: `LOWER()` 제거 |
| `TherapyPostRepository.java` | `searchByKeywordAndVisibility`: `LOWER()` 제거 |
| `PostService.java` | 주석 수정: `similarity` → `word_similarity` |
