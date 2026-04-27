# Commit 6: JPQL 키워드 검색 컬럼 content → searchText 변경

## 무엇을 했나

`/api/v1/posts` 엔드포인트의 JPQL 키워드 검색 쿼리 2개에서:
- `LOWER(p.content)` → `LOWER(p.searchText)` 변경

## 왜 했나

### V27 회귀 리스크

**시간순 문제:**
```
V16: idx_therapy_posts_content_trgm 인덱스 생성 (content에 GIN)
V25: search_text 컬럼 + idx_therapy_posts_search_text_trgm 추가
V27: idx_therapy_posts_content_trgm 삭제 ← 우리가 방금 한 것!
```

V27에서 content 인덱스를 지웠는데, JPQL `searchByKeyword`는 여전히 `content` 컬럼을 검색 중.
→ 인덱스 없는 컬럼을 LIKE 검색 → **Seq Scan 확정** → 데이터 많아지면 성능 저하.

### searchText가 더 나은 이유

| | content | searchText |
|---|---------|------------|
| 내용 | 게시글 본문 전체 | title + content(100자) + therapyArea(한글) + ageGroup(한글) |
| GIN 인덱스 | ❌ (V27에서 삭제) | ✅ `idx_therapy_posts_search_text_trgm` |
| 검색 범위 | 본문만 | 본문 + 제목 + 치료 영역 + 연령대 |

**검색어 "감각통합"으로 검색 시:**
- content 검색: 본문에 "감각통합"이 있어야 매칭
- searchText 검색: 본문, 제목, 또는 therapyArea가 SENSORY_INTEGRATION이면 "감각통합" 포함 → 매칭

## 핵심 개념 학습

### 1. 왜 ILIKE로 바꾸지 않았나?

**JPQL에서는 `ILIKE`를 쓸 수 없다.**
JPQL은 SQL 표준의 서브셋이고, `ILIKE`는 PostgreSQL 전용 문법.

**대안:**
```java
// JPQL — LOWER() + LIKE로 case-insensitive 흉내
LOWER(p.searchText) LIKE LOWER(CONCAT('%', :keyword, '%'))

// 네이티브 SQL — PostgreSQL ILIKE 직접 사용
p.search_text ILIKE '%' || :keyword || '%'
```

**JPQL을 유지한 이유:**
- `@EntityGraph(attributePaths = "author")`를 그대로 사용 가능
- 네이티브 SQL로 바꾸면 EntityGraph가 안 되고 별도 JOIN 필요
- LOWER()+LIKE가 GIN trigram 인덱스를 타는지는 EXPLAIN으로 확인 필요
  - 타면: 현재 상태 유지
  - 안 타면: 그때 네이티브 SQL로 전환

### 2. LOWER()+LIKE와 GIN trigram 인덱스

PostgreSQL의 GIN trigram 인덱스(`gin_trgm_ops`)는 다음을 지원:
- `column % 'text'` ✅
- `column <% 'text'` ✅
- `column ILIKE '%text%'` ✅
- `column LIKE '%text%'` ✅
- `LOWER(column) LIKE LOWER('%text%')` ⚠️ **불확실**

**왜 불확실한가:**
PostgreSQL 쿼리 플래너가 `LOWER()` 래핑을 "풀어서" 인덱스를 활용하려고 시도하지만,
항상 성공하는 것은 아님. 버전, 통계, 데이터 크기에 따라 달라짐.

**확인 방법 (프로덕션 배포 후):**
```sql
EXPLAIN ANALYZE
SELECT * FROM therapy_posts
WHERE LOWER(search_text) LIKE LOWER('%감각통합%');
```

Seq Scan이 나오면 → 네이티브 SQL + ILIKE로 전환 필요.

### 3. 두 엔드포인트를 모두 유지하는 이유

| | `/api/v1/posts` | `/api/v1/posts/search` |
|---|---|---|
| **UX 목적** | 브라우징 + 선택적 필터 | 전용 검색 |
| **정렬** | LATEST / MOST_VIEWED | RELEVANCE (점수순) |
| **페이지네이션** | offset (page, size) | 커서 (lastScore, lastId) |
| **키워드** | 선택 (없으면 전체 목록) | 필수 |

같은 키워드 검색이라도 **정렬 방식과 UX 의도가 다르므로** 둘 다 필요.

## 변경된 쿼리

### searchByKeyword (Before → After)
```java
// Before
AND LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%'))

// After
AND LOWER(p.searchText) LIKE LOWER(CONCAT('%', :keyword, '%'))
```

### searchByKeywordAndVisibility (동일 변경)

## 수정 파일

| 파일 | 변경 |
|------|------|
| `TherapyPostRepository.java` | `searchByKeyword`: `p.content` → `p.searchText` |
| `TherapyPostRepository.java` | `searchByKeywordAndVisibility`: `p.content` → `p.searchText` |
