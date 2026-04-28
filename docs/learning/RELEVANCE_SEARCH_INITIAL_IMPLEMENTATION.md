# RELEVANCE 검색 — 초기 구현 정리 (수정 전 스냅샷)

> 이 문서는 `feat/search-relevance` 브랜치에서 **에러를 수정하기 전** 시점의 구현 내용을 학습용으로 정리한 기록이다.
> 이후 워킹 트리에 가해진 수정 사항(BigDecimal 전환, `%` 연산자 + `SET LOCAL` 임계값 도입 등)은 포함하지 않는다.
> 출처: 브랜치의 4개 커밋 (`43454d8`, `09e686b`, `a26ccc0`, `decc0ac`)

---

## 1. 목표와 큰 그림

`GET /posts/search` 라는 신규 엔드포인트를 추가해 **PostgreSQL `pg_trgm` 기반 RELEVANCE(연관도) 검색**을 무한스크롤로 제공하는 기능이다.

기존 `GET /posts` 의 offset 페이지네이션과는 분리되며, 다음 두 가지가 핵심 차이점이다.

1. **정렬 기준**: pg_trgm 의 `similarity(search_text, keyword)` 점수 DESC, 동률 시 `id` DESC
2. **페이지네이션**: `(lastScore, lastId)` 두 값을 함께 쓰는 **복합 커서** 기반

이를 위해 다음 4개 커밋이 순서대로 들어왔다.

| 커밋 | 제목 | 핵심 |
|------|------|------|
| `43454d8` | feat(post): RELEVANCE 검색용 search_text 컬럼 + GIN trigram 인덱스 (V25) | 마이그레이션 V25 |
| `09e686b` | feat(post): TherapyPost.searchText 필드 + AgeGroup/PostSortType 확장 | JPA 필드, enum |
| `a26ccc0` | feat(post): RELEVANCE 검색을 커서 기반(lastScore, lastId) 페이지네이션으로 전환 | Repository, Service |
| `decc0ac` | feat(post): GET /posts/search 무한스크롤 검색 엔드포인트 + SearchCursorResponse DTO | Controller, DTO |

---

## 2. DB 레이어 — V25 마이그레이션

`src/main/resources/db/migration/V25__add_post_search_text_for_relevance.sql`

### 2.1 확장 보장

```sql
-- pg_trgm extension (V16에서 이미 활성화됐지만 안전 차원)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

이전 마이그레이션(V16)에서 이미 `pg_trgm` 을 활성화한 상태지만, 마이그레이션의 멱등성을 위해 다시 한 번 `IF NOT EXISTS` 로 호출했다.

### 2.2 search_text 컬럼 추가

```sql
ALTER TABLE therapy_posts
    ADD COLUMN IF NOT EXISTS search_text TEXT;
```

- 타입은 `TEXT` (길이 제한 없음).
- `NULL` 허용. 이유: 신규 게시글은 JPA 엔티티 쪽에서 채우고, 기존 행은 백필 단계에서 채우므로 두 단계 모두 안전하게 진행하기 위해 일부러 NOT NULL 을 걸지 않았다.

### 2.3 기존 행 백필

```sql
UPDATE therapy_posts
SET search_text = TRIM(
    COALESCE(title, '') || ' ' ||
    COALESCE(LEFT(content, 100), '') || ' ' ||
    CASE therapy_area
        WHEN 'UNSPECIFIED'         THEN '선택안함'
        WHEN 'SENSORY_INTEGRATION' THEN '감각통합'
        WHEN 'SPEECH'              THEN '언어치료'
        WHEN 'OCCUPATIONAL'        THEN '작업치료'
        WHEN 'COGNITIVE'           THEN '인지치료'
        WHEN 'PHYSICAL'            THEN '물리치료'
        WHEN 'ART'                 THEN '미술치료'
        WHEN 'MUSIC'               THEN '음악치료'
        WHEN 'PLAY'                THEN '놀이치료'
        WHEN 'BEHAVIOR'            THEN '행동치료'
        ELSE ''
    END || ' ' ||
    CASE age_group
        WHEN 'AGE_0_2'     THEN '0세 2세'
        WHEN 'AGE_3_5'     THEN '3세 5세'
        WHEN 'AGE_6_12'    THEN '6세 12세'
        WHEN 'AGE_13_18'   THEN '13세 18세'
        WHEN 'AGE_19_64'   THEN '19세 64세'
        WHEN 'AGE_65_PLUS' THEN '65세 이상'
        ELSE ''
    END
)
WHERE search_text IS NULL;
```

핵심 설계 포인트:

- **search_text 의 구성요소**: `title` + `content 앞 100자` + `therapy_area 한글` + `age_group 한글`.
  - 본문 전체가 아니라 앞 100자만 쓴다 → trigram 인덱스 크기/유사도 노이즈 통제.
  - therapy_area, age_group 의 enum 값을 한글로 풀어서 넣는다 → 사용자가 "감각통합" 같은 한글 키워드로 검색해도 점수가 잡히게 한다.
- **enum ↔ 한글 매핑은 Java 코드와 1:1 일치 필수**: `AgeGroup.description`, `TherapyArea.description` 과 동일해야 한다. SQL 과 Java 가 따로 놀면 검색 점수가 갈라진다.
- `WHERE search_text IS NULL` 가드: 마이그레이션이 재실행돼도 이미 채워진 행을 덮어쓰지 않는다.

### 2.4 GIN trigram 인덱스

```sql
CREATE INDEX IF NOT EXISTS idx_therapy_posts_search_text_trgm
    ON therapy_posts USING GIN (search_text gin_trgm_ops);
```

- `gin_trgm_ops` 는 trigram 매칭을 GIN 인덱스로 가속해 주는 연산자 클래스.
- **이 시점의 의도**는 `similarity(...) > 임계값` 과 `ILIKE '%...%'` 두 술어 모두 이 인덱스로 가속되리라는 것이었다.
  - 실제 PostgreSQL 동작에서는 `similarity()` 함수 호출 형태는 GIN 인덱스를 직접 못 타고, `%` 연산자를 써야 인덱스를 탄다. → 이 부분이 후속 수정의 트리거가 된다.

---

## 3. 도메인 레이어 — 엔티티 / enum

### 3.1 `TherapyPost.searchText` 필드 추가

`post/domain/TherapyPost.java`

```java
@Column(name = "search_text", columnDefinition = "TEXT")
private String searchText;
```

생성자(`create`)와 `update()` 두 곳 모두에서 `buildSearchText(...)` 를 호출해 항상 일관된 포맷으로 채운다.

```java
private static String buildSearchText(
        String title,
        String content,
        TherapyArea therapyArea,
        AgeGroup ageGroup
) {
    String t = title == null ? "" : title;
    String c = content == null
            ? ""
            : content.substring(0, Math.min(100, content.length()));
    String a = therapyArea == null ? "" : therapyArea.getDescription();
    String g = ageGroup == null ? "" : ageGroup.getDescription();
    return (t + " " + c + " " + a + " " + g).trim();
}
```

설계 포인트:

- 신규/수정 두 경로 모두 같은 빌더를 호출 → V25 백필 SQL 과 1:1 동일한 포맷.
- `title` 은 nullable(과거 호환), `content` 는 non-null 이지만 길이 100 안전 자르기 적용.
- `description()` 호출은 enum 의 한글 표시값을 사용하는데, 이게 V25 의 `CASE ... THEN '한글'` 분기와 정확히 같아야 한다.

### 3.2 `AgeGroup` enum 확장

`post/domain/AgeGroup.java`

```java
public enum AgeGroup {
    UNSPECIFIED(""),
    AGE_0_2("0세 2세"),
    AGE_3_5("3세 5세"),
    AGE_6_12("6세 12세"),
    AGE_13_18("13세 18세"),
    AGE_19_64("19세 64세"),
    AGE_65_PLUS("65세 이상");

    private final String description;
    // getter ...
}
```

각 enum 값에 한글 description 을 부여 — search_text 빌드용.

### 3.3 `PostSortType` 에 RELEVANCE 추가

`post/domain/PostSortType.java`

```java
public enum PostSortType {
    LATEST,
    MOST_VIEWED,
    RELEVANCE
}
```

다만 RELEVANCE 는 새로운 `/posts/search` 엔드포인트 전용이라, `getPosts()` 의 `toSort()` 가 RELEVANCE 를 받았을 때는 LATEST 로 폴백한다.

```java
case LATEST, RELEVANCE -> Sort.by(
        Sort.Order.desc("createdAt"),
        Sort.Order.desc("id")
);
```

이렇게 한 이유: RELEVANCE 는 native query 와 (lastScore, lastId) 커서가 필요해서 `Pageable.Sort` 만으로는 표현할 수 없다. `getPosts()` 경로로 잘못 들어와도 안전하게 LATEST 로 떨어뜨리고, 진짜 RELEVANCE 는 무조건 `/posts/search` 로 가도록 강제했다.

---

## 4. 리포지터리 레이어 — native query 4종

`post/repository/TherapyPostRepository.java`

### 4.1 분기 구조

같은 RELEVANCE 검색이지만 다음 두 축으로 4개의 메서드가 필요하다.

| 페이지 | visibility 필터 | 메서드 |
|--------|-----------------|--------|
| 첫 페이지 | 없음 (THERAPIST/ADMIN) | `searchIdsByRelevanceFirstPage` |
| 다음 페이지 | 없음 | `searchIdsByRelevanceNextPage` |
| 첫 페이지 | PUBLIC 만 (USER) | `searchIdsByRelevanceFirstPageAndVisibility` |
| 다음 페이지 | PUBLIC 만 | `searchIdsByRelevanceNextPageAndVisibility` |

**왜 메서드를 합치지 않았나?**: 다음 페이지 쿼리는 `(lastScore, lastId)` 커서 조건이 추가되고, visibility 필터 분기는 인덱스 사용 패턴과 파라미터 셋이 달라 합치면 가독성이 더 떨어진다. 4개로 명시 분리.

### 4.2 첫 페이지 (visibility 필터 없음) — 수정 전 원본

```sql
SELECT p.id, similarity(p.search_text, :keyword) AS score
FROM therapy_posts p
WHERE p.deleted_at IS NULL
  AND (CAST(:therapyArea AS text) IS NULL OR p.therapy_area = CAST(:therapyArea AS text))
  AND (CAST(:postType AS text) IS NULL OR p.post_type = CAST(:postType AS text))
  AND (
        similarity(p.search_text, :keyword) > 0.03
     OR p.search_text ILIKE '%' || :escapedKeyword || '%' ESCAPE '\\'
  )
ORDER BY score DESC, p.id DESC
LIMIT :limit
```

```java
List<Object[]> searchIdsByRelevanceFirstPage(
        @Param("keyword") String keyword,
        @Param("escapedKeyword") String escapedKeyword,
        @Param("therapyArea") String therapyArea,
        @Param("postType") String postType,
        @Param("limit") int limit
);
```

설계 포인트들:

- **`SELECT p.id, similarity(...) AS score`**: ID 와 점수만 가져오는 **두 단계 fetch** 의 첫 단계. 엔티티 본문은 안 가져온다 → native query 에서 `@EntityGraph` 가 동작하지 않아 N+1 회피용.
- **`CAST(:therapyArea AS text) IS NULL OR ...` 패턴**: PostgreSQL native query 에서 nullable 파라미터를 안전하게 다루기 위해 명시적으로 `text` 로 캐스트. JPA 가 enum/null 을 넘길 때 타입 추론을 못 해서 발생하는 `could not determine data type of parameter` 회피.
- **두 술어 OR 매칭**:
  - `similarity(p.search_text, :keyword) > 0.03` — pg_trgm similarity 점수가 0.03 초과인 경우
  - `p.search_text ILIKE '%' || :escapedKeyword || '%' ESCAPE '\\'` — substring 매칭 fallback
- **임계값 0.03**: 한국어 짧은 키워드(2글자)는 trigram similarity 가 거의 0 으로 떨어지는 회귀가 있어 임계값을 매우 낮게 잡은 값. (메모리 노트: `feedback_korean_trigram_search.md` 참고)
- **`:keyword` vs `:escapedKeyword` 분리**:
  - `similarity()` 는 raw 키워드를 받아야 한다 (메타문자 의미 없음).
  - `ILIKE` 는 `%`, `_`, `\\` 가 메타문자라 escape 된 키워드를 써야 한다.
  - 한 파라미터로 묶으면 한쪽이 깨진다. (메모리 노트: `feedback_similarity_vs_like_escape.md` 참고)
- **`ORDER BY score DESC, p.id DESC`**: 동점 시 ID 역순. 커서 비교에서도 같은 순서를 강제해야 안정적.
- **`LIMIT :limit`**: 호출자는 `size + 1` 을 넘긴다 → "한 개 더 가져와서 다음 페이지 존재 여부 판정" 패턴.
- **반환 타입 `List<Object[]>`**: 두 컬럼(`id`, `score`)을 그대로 받기 위해 DTO 매핑 없이 Object[] 사용. Service 단에서 풀어서 처리.

### 4.3 다음 페이지 (visibility 필터 없음) — 수정 전 원본

```sql
SELECT p.id, similarity(p.search_text, :keyword) AS score
FROM therapy_posts p
WHERE p.deleted_at IS NULL
  AND (CAST(:therapyArea AS text) IS NULL OR p.therapy_area = CAST(:therapyArea AS text))
  AND (CAST(:postType AS text) IS NULL OR p.post_type = CAST(:postType AS text))
  AND (
        similarity(p.search_text, :keyword) > 0.03
     OR p.search_text ILIKE '%' || :escapedKeyword || '%' ESCAPE '\\'
  )
  AND (
        similarity(p.search_text, :keyword) < :lastScore
     OR (similarity(p.search_text, :keyword) = :lastScore AND p.id < :lastId)
  )
ORDER BY score DESC, p.id DESC
LIMIT :limit
```

```java
List<Object[]> searchIdsByRelevanceNextPage(
        @Param("keyword") String keyword,
        @Param("escapedKeyword") String escapedKeyword,
        @Param("therapyArea") String therapyArea,
        @Param("postType") String postType,
        @Param("lastScore") double lastScore,
        @Param("lastId") long lastId,
        @Param("limit") int limit
);
```

핵심 — **복합 커서 조건** 한 줄:

```sql
similarity(...) < :lastScore
OR (similarity(...) = :lastScore AND p.id < :lastId)
```

이게 (score DESC, id DESC) 정렬의 keyset pagination 정형이다:

> "현재 페이지의 마지막 행보다 점수가 더 낮거나, 점수가 같다면 id 가 더 작은 행"

`(lastScore, lastId)` 두 값이 한 쌍으로 항상 같이 와야 한다. 한 쪽만 오면 컨트롤러에서 400.

⚠️ **이 시점의 잠재 문제** (수정 전이라 그대로 둔 부분):

1. `similarity()` 함수 호출은 GIN 인덱스를 못 타서, 게시글이 늘어날수록 풀스캔에 가까워질 수 있다.
2. `score` 컬럼이 PG 의 `real(float4)` 인데 Java `double` 로 받아서 클라이언트에 내려준 뒤 다음 요청에 다시 `double` 로 받으면 부동소수 round-trip 에서 미세하게 어긋날 수 있다 → 동등 비교 `= :lastScore` 가 빗나가 같은 행을 두 번 보거나 건너뛸 위험.

이 두 가지를 해결하는 게 후속 워킹 트리 수정이다 (`%` 연산자 + `SET LOCAL similarity_threshold` + `numeric(10,8)` 캐스트 + `BigDecimal`).

### 4.4 visibility 필터 버전

(c), (d) 는 (a), (b) 와 본문은 같고 한 줄만 추가된다:

```sql
AND p.visibility = CAST(:visibility AS text)
```

USER 권한이 호출하면 이쪽 두 메서드로 분기되어 `PUBLIC` 만 보게 된다. 같은 native query 를 그대로 두지 않고 분기시킨 이유는 인덱스 사용 패턴의 명시성과 가독성이다.

### 4.5 ID → 엔티티 두 번째 fetch

```java
@EntityGraph(attributePaths = "author")
@Query("SELECT p FROM TherapyPost p WHERE p.id IN :ids")
List<TherapyPost> findAllByIdInWithAuthor(@Param("ids") List<Long> ids);
```

native query 결과로 받은 ID 리스트로 **JPQL** 쿼리를 한 번 더 던져 `author` 까지 fetch join. JPQL 이라서 `@EntityGraph` 가 정상 동작 → N+1 없이 author 까지 한 방.

다만 `IN :ids` 는 결과 정렬 순서를 보장하지 않으므로, Service 에서 native 결과의 ID 순서대로 다시 정렬해야 한다 (4.7 참고).

---

## 5. 서비스 레이어 — `PostService.findPostsByRelevance`

`post/service/PostService.java`

### 5.1 외부 진입 메서드

```java
/**
 * RELEVANCE 검색 (무한스크롤) — 외부 진입 메서드.
 * lastScore/lastId 가 모두 null 이면 첫 페이지, 모두 있으면 다음 페이지.
 * 컨트롤러에서 두 값의 쌍 검증을 통과한 뒤 호출되는 것을 가정한다.
 */
public SearchCursorResponse searchPostsByRelevance(
        PostSearchCondition condition,
        Double lastScore,
        Long lastId,
        int size,
        UserRole role
) {
    boolean publicOnly = !visibilityPolicy.canViewPrivate(role);
    return findPostsByRelevance(condition, publicOnly, lastScore, lastId, size);
}
```

- 시그니처: `Double lastScore` (박스 타입 → null 허용) + `Long lastId`. → 첫 페이지/다음 페이지를 한 메서드로 처리.
- `visibilityPolicy.canViewPrivate(role)` 결과를 뒤집어 `publicOnly` 를 만든 뒤 내부 메서드로 위임.
- **이 시점에는 클래스 레벨 `@Transactional(readOnly = true)` 가 그대로 적용**되었다 (메서드 단위 오버라이드 없음). 후속 수정에서 `@Transactional` 을 메서드에 명시해 readOnly 를 풀게 된다.

### 5.2 내부 구현

```java
private SearchCursorResponse findPostsByRelevance(
        PostSearchCondition condition,
        boolean publicOnly,
        Double lastScore,
        Long lastId,
        int size
) {
    // similarity 는 raw, ILIKE 는 escaped — 두 함수가 메타문자 의미가 달라 분리 필수
    String rawKeyword = condition.getKeyword().trim();
    String escapedKeyword = condition.getEscapedKeyword().trim();
    String area = condition.getTherapyArea() != null ? condition.getTherapyArea().name() : null;
    String type = condition.getPostType() != null ? condition.getPostType().name() : null;

    int limit = size + 1; // hasNext 판별용 take+1 조회
    boolean firstPage = (lastScore == null && lastId == null);

    List<Object[]> rows;
    if (firstPage) {
        rows = publicOnly
                ? therapyPostRepository.searchIdsByRelevanceFirstPageAndVisibility(
                        rawKeyword, escapedKeyword, area, type, Visibility.PUBLIC.name(), limit)
                : therapyPostRepository.searchIdsByRelevanceFirstPage(
                        rawKeyword, escapedKeyword, area, type, limit);
    } else {
        rows = publicOnly
                ? therapyPostRepository.searchIdsByRelevanceNextPageAndVisibility(
                        rawKeyword, escapedKeyword, area, type, Visibility.PUBLIC.name(),
                        lastScore, lastId, limit)
                : therapyPostRepository.searchIdsByRelevanceNextPage(
                        rawKeyword, escapedKeyword, area, type, lastScore, lastId, limit);
    }
    // ... 이어서
}
```

분기 흐름:

1. 두 종류 키워드 (raw / escaped) 를 미리 만들어 둔다 → 리포지터리에 OR 매칭 두 슬롯에 각각 들어간다.
2. enum 필터(`therapyArea`, `postType`) 는 `.name()` 으로 문자열로 변환 → native 쿼리는 enum 을 모르고 PG 의 `text` 로만 비교한다.
3. `limit = size + 1` → "한 개 더" 패턴.
4. `firstPage` 판정: `lastScore == null && lastId == null`. (컨트롤러에서 쌍 검증을 먼저 했으므로 한 쪽만 null 인 경우는 도달하지 않는다.)
5. (firstPage, publicOnly) 두 boolean 로 4 메서드 중 하나를 고른다.

### 5.3 hasNext 판정 + 트림

```java
boolean hasNextData = rows.size() > size;
List<Object[]> pageRows = hasNextData ? rows.subList(0, size) : rows;

if (pageRows.isEmpty()) {
    return new SearchCursorResponse(
            List.of(),
            new SearchCursorResponse.SearchCursorMeta(false, null, null)
    );
}
```

- size+1 개 받아왔으니 size 를 넘으면 다음 페이지가 있는 것으로 본다.
- 트림은 `subList(0, size)`. 트림 후 마지막 원소가 다음 커서 계산 대상이 된다.
- 빈 결과는 빠르게 빈 응답으로 반환.

### 5.4 두 단계 fetch — 두 번째 단계

```java
List<Long> ids = pageRows.stream()
        .map(r -> ((Number) r[0]).longValue())
        .toList();
Map<Long, TherapyPost> byId = therapyPostRepository.findAllByIdInWithAuthor(ids).stream()
        .collect(Collectors.toMap(TherapyPost::getId, Function.identity()));

// IN 절은 정렬을 보존하지 않으므로 native 결과의 ID 순서대로 재정렬
List<TherapyPostSummaryResponse> items = ids.stream()
        .map(byId::get)
        .filter(Objects::nonNull)
        .map(p -> TherapyPostSummaryResponse.from(p, false))
        .toList();
```

핵심 트릭:

- native query 가 (score, id) 순서를 만들었으니 JPQL 의 `IN` 결과를 그 순서로 **재정렬해야 한다**. PostgreSQL 의 `IN` 은 어떤 순서로든 결과를 반환할 수 있다.
- `ids` 리스트(이미 score DESC, id DESC 정렬됨)를 기준으로 Map 에서 꺼내며 다시 매핑한다 → 정렬 보존.
- `Objects::nonNull` 가드: native 와 JPQL 사이 race / soft-delete 등으로 일부 ID 가 사라지는 경우를 안전하게 걸러낸다.

### 5.5 다음 커서 계산 — 수정 전 원본

```java
Double nextScore = null;
Long nextId = null;
if (hasNextData) {
    Object[] lastRow = pageRows.get(pageRows.size() - 1);
    nextId = ((Number) lastRow[0]).longValue();
    nextScore = ((Number) lastRow[1]).doubleValue();
}
```

- `hasNextData == false` 면 둘 다 null → 클라이언트가 마지막 페이지로 인식.
- `pageRows` 는 이미 size 개로 트림된 상태. **그 마지막 원소** 의 `(id, score)` 가 다음 페이지의 시작점이 된다.
- `Object[]` 의 인덱스는 SELECT 컬럼 순서: `[0] = p.id`, `[1] = score`.
- score 를 `((Number) lastRow[1]).doubleValue()` 로 변환 → 그대로 응답에 실어 클라이언트에 내려간다.

⚠️ **이 시점의 잠재 문제** (수정 전이라 그대로 둔 부분):

- PG `real(float4)` → Java `double` → JSON 직렬화 → 클라이언트 → 다음 요청 JSON → Java `double` → SQL `:lastScore` 의 왕복.
- 이 왕복에서 `0.123456789` 가 `0.12345679` 같은 식으로 어긋날 수 있고, 그러면 동등 비교 `similarity(...) = :lastScore` 가 빗나가 **같은 행을 두 번 보내거나, 한 행을 건너뛰는** 미묘한 버그가 생길 수 있다.

### 5.6 최종 응답

```java
return new SearchCursorResponse(
        items,
        new SearchCursorResponse.SearchCursorMeta(hasNextData, nextScore, nextId)
);
```

- `data` (= `items`) 는 정렬 보존된 `TherapyPostSummaryResponse` 리스트.
- `meta` 는 hasNextData + 다음 커서 두 값.

> ※ 이 시점에 컨트롤러는 응답을 받은 뒤 한 번 더 후처리(스크랩 마킹) 한다 — 아래 6.3 참고.

---

## 6. 컨트롤러 레이어 — `GET /posts/search`

`post/controller/PostController.java`

### 6.1 엔드포인트 정의 — 수정 전 원본

```java
@Operation(summary = "게시글 검색 (무한스크롤)",
        description = "RELEVANCE 정렬 전용. (lastScore, lastId) 커서 기반. 첫 페이지는 두 값 모두 생략")
@GetMapping("/search")
public ResponseEntity<ApiResponse<SearchCursorResponse>> searchPosts(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @RequestParam String keyword,
        @RequestParam(required = false) TherapyArea therapyArea,
        @RequestParam(required = false) PostType postType,
        @RequestParam(required = false) Double lastScore,
        @RequestParam(required = false) Long lastId,
        @RequestParam(defaultValue = "10") int size
) {
    // ...
}
```

- `keyword` 는 필수. (RELEVANCE 검색은 키워드 없는 호출이 의미 없음.)
- `therapyArea`, `postType` 은 선택 필터.
- `lastScore`, `lastId` 두 커서값은 첫 페이지에선 생략, 다음 페이지에서 함께 전달.
- `size` 기본값 10.
- `userDetails` 는 비인증 호출 가능 (`@AuthenticationPrincipal` + null 허용).

### 6.2 커서 쌍 검증

```java
// 커서는 (lastScore, lastId) 가 항상 쌍으로 와야 한다. 한쪽만 오면 클라이언트 버그 → 400.
if ((lastScore == null) != (lastId == null)) {
    throw new CustomException(ErrorCode.INVALID_INPUT);
}
```

`!=` 를 boolean 두 값에 적용 → "한쪽만 null 일 때" 정확히 잡는 패턴. 컨트롤러 단에서 미리 차단해 Service 가 (lastScore == null && lastId == null) 한 가지만 신경 쓰면 되도록 만든다.

### 6.3 호출 + 스크랩 후처리

```java
PostSearchCondition condition = new PostSearchCondition(keyword, therapyArea, postType);
UserRole userRole = userDetails != null ? userDetails.getUserRole() : UserRole.USER;
SearchCursorResponse response = postService.searchPostsByRelevance(
        condition, lastScore, lastId, size, userRole
);

Long userId = userDetails != null ? userDetails.getUserId() : null;
List<Long> postIds = response.getData().stream()
        .map(TherapyPostSummaryResponse::getId).toList();
Set<Long> scrappedIds = scrapService.getScrappedPostIds(userId, postIds);
response.getData().forEach(post -> post.markScrapped(scrappedIds.contains(post.getId())));

return ResponseEntity.ok(ApiResponse.success(response));
```

흐름:

1. 검색 조건 DTO 조립.
2. 비인증 사용자는 USER 권한으로 간주 → `publicOnly` 로 분기됨.
3. Service 호출.
4. **스크랩 마킹**: 응답으로 받은 게시글 ID 들에 대해 현재 사용자의 스크랩 여부를 한 번에 조회 → 각 summary DTO 에 markScrapped(true/false) 를 채운다.
   - userId 가 null 이면 ScrapService 가 빈 Set 을 돌려주는 패턴(다른 엔드포인트들과 동일).
5. `ApiResponse.success(...)` 로 표준 응답 포장.

---

## 7. DTO — `SearchCursorResponse` (수정 전 원본)

`post/dto/SearchCursorResponse.java`

```java
package com.therapyCommunity_Vol1.backend.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * RELEVANCE 검색 (무한스크롤) 전용 응답 DTO.
 *
 * pg_trgm similarity 점수 + id 기반 커서를 사용하므로 단일 문자열 nextCursor 를
 * 쓰는 {@code CursorPagedResponse} 와 분리해서 가져간다.
 *
 * - data : 현재 페이지 게시글 요약 목록
 * - meta : hasNextData / nextScore / nextId
 *   nextScore, nextId 는 마지막 페이지일 경우 모두 null
 */
@Getter
@AllArgsConstructor
public class SearchCursorResponse {

    private final List<TherapyPostSummaryResponse> data;
    private final SearchCursorMeta meta;

    @Getter
    @AllArgsConstructor
    public static class SearchCursorMeta {
        private final boolean hasNextData;
        private final Double nextScore;
        private final Long nextId;
    }
}
```

설계 포인트:

- **별도 DTO 인 이유**: 기존 `CursorPagedResponse<T>` 는 단일 문자열 `nextCursor` (Base64 인코딩된 createdAt+id) 를 쓰는데, RELEVANCE 검색은 `(score, id)` 두 값이 노출되어야 한다. 의미가 달라 합치지 않고 분리.
- `hasNextData` (boolean) + `nextScore` (Double) + `nextId` (Long) 셋만 들어있는 평면적인 meta.
- **타입 선택 (수정 전)**: `Double`. 이게 뒤이어 BigDecimal 로 바뀌게 되는 부분.

---

## 8. 두 단계 fetch 가 필요한 이유 (정리)

처음 보면 "왜 ID 만 먼저 가져와서 또 fetch 하지?" 가 가장 어색한 부분이라, 이유를 한 번 더 정리해 둔다.

1. RELEVANCE 정렬은 PostgreSQL 함수(`similarity()`) 를 써야 하므로 **native query** 가 필요하다.
2. native query 에서는 Spring Data JPA 의 `@EntityGraph` 가 동작하지 않는다.
3. 그렇다고 native 쿼리에서 author 까지 join 해서 엔티티 매핑하는 건 컬럼 매핑이 복잡해진다.
4. 그래서 native 는 `(id, score)` 만 반환 → JPQL 한 방으로 ID 들을 fetch join 으로 가져옴 → N+1 회피.
5. 단, JPQL `IN` 의 결과는 정렬을 보장하지 않으므로 native 결과의 ID 순서로 **수동 재정렬** 필요.

---

## 9. 이 시점의 잠재 이슈 한눈에 보기

수정 전 시점에서 코드에 남아있던 잠재 문제들 (이후 워킹 트리 수정의 동기):

1. **`similarity() > 0.03` 술어가 GIN 인덱스를 못 탄다** — 함수 호출 형태라 PG 가 인덱스 추출 대상 키워드를 만들지 못함. 데이터가 늘면 풀스캔.
2. **`real(float4)` ↔ Java `double` round-trip 정밀도 손실** — 커서 동등 비교(`= :lastScore`) 가 빗나갈 수 있어 페이지 경계가 흐트러질 수 있음.
3. **`@Transactional(readOnly = true)` 클래스 기본값 그대로** — 이후에 `SET LOCAL` 같은 트랜잭션 스코프 설정을 안전하게 실행하려면 readOnly 를 풀어야 함.

이 세 가지가 후속 수정 (`%` 연산자 + `SET LOCAL pg_trgm.similarity_threshold = 0.03` + `numeric(10,8)` 캐스트 + `BigDecimal` 전환 + 메서드 단위 `@Transactional`) 의 동기다. 이 문서의 범위는 그 직전까지.

---

## 10. 학습 체크리스트

이 구현을 다시 만들 수 있는지 스스로 체크할 때 쓸 질문들:

- [ ] 왜 `search_text` 컬럼을 만들었고, 거기에 어떤 값들을 담았는가? (title + content 100자 + therapy_area 한글 + age_group 한글)
- [ ] 왜 V25 SQL 의 `CASE` 한글 매핑이 Java enum description 과 같아야 하는가? (검색 점수가 갈라지지 않게)
- [ ] 왜 `PostSortType.RELEVANCE` 가 `getPosts()` 에서 LATEST 로 폴백되는가? (RELEVANCE 는 native + 복합 커서 전용 엔드포인트)
- [ ] 왜 native 쿼리 4개로 분기했는가? (페이지 × visibility 두 축)
- [ ] 왜 키워드 파라미터를 `:keyword` 와 `:escapedKeyword` 두 개로 분리하는가? (similarity 는 raw, ILIKE 는 escape 필요)
- [ ] `(lastScore, lastId)` 복합 커서 조건의 정확한 SQL 형태는? (`< lastScore OR (= lastScore AND id < lastId)`)
- [ ] `LIMIT :limit` 에 왜 `size + 1` 을 넣는가? (hasNext 판정용)
- [ ] 두 단계 fetch 가 왜 필요한가? (native + EntityGraph 비호환 → ID 만 먼저 → JPQL 로 author fetch)
- [ ] JPQL `IN` 후 왜 수동 재정렬해야 하는가? (IN 은 정렬 보존 안 함)
- [ ] `(lastScore == null) != (lastId == null)` 가 왜 검증 한 줄로 충분한가? (boolean XOR 패턴)
- [ ] 컨트롤러에서 스크랩 마킹은 언제, 어떻게 후처리되는가?
- [ ] 이 시점의 잠재 문제 3가지는 무엇인가? (인덱스 미사용 / 정밀도 손실 / readOnly 트랜잭션)
