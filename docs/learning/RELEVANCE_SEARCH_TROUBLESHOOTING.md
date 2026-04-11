# RELEVANCE 검색 — 트러블슈팅 기록

> 이 문서는 [`RELEVANCE_SEARCH_INITIAL_IMPLEMENTATION.md`](./RELEVANCE_SEARCH_INITIAL_IMPLEMENTATION.md) 의 후속이다.
> 초기 구현(4개 커밋)이 들어간 뒤 발견된 문제들과, 이를 워킹 트리에서 고친 내용을 정리한다.
> 변경 범위: `PostController.java`, `SearchCursorResponse.java`, `TherapyPostRepository.java`, `PostService.java` 4개 파일.

---

## 0. 문제 한눈에 보기

| # | 증상/위험 | 근본 원인 | 수정 방향 |
|---|-----------|-----------|-----------|
| 1 | `similarity(...) > 0.03` 술어가 GIN trigram 인덱스를 못 탄다 → 데이터가 늘면 풀스캔 | PG 의 `gin_trgm_ops` 는 **연산자(`%`)** 호출은 인덱스로 추출하지만, **함수(`similarity()`) 호출 + 비교 연산** 형태는 인덱스 추출 대상이 아니다 | 술어를 `p.search_text % :keyword` 로 교체. 임계값은 `SET LOCAL pg_trgm.similarity_threshold = 0.03` 로 트랜잭션 스코프 지정 |
| 2 | 페이지네이션이 미세하게 어긋날 수 있음 — 같은 행을 두 번 보거나 한 행을 건너뜀 | PG `real(float4)` → Java `double` → JSON → 클라이언트 → 다음 요청 → Java `double` → SQL `:lastScore` 왕복에서 부동소수 정밀도 손실. `similarity(...) = :lastScore` 동등 비교가 빗나감 | 점수 컬럼을 `numeric(10,8)` 로 캐스트 + Java/DTO/파라미터를 모두 `BigDecimal` 로 통일 |
| 3 | 위 1번 수정에서 도입한 `SET LOCAL` 이 readOnly 트랜잭션에서 거부될 위험 | 클래스 레벨 `@Transactional(readOnly = true)` 가 그대로 적용됨. PG 자체는 READ ONLY 에서도 `SET LOCAL` 을 허용하지만 JDBC/Hibernate 일부 버전에서 거부된 사례가 있음 | `searchPostsByRelevance` 메서드에 `@Transactional` 명시 → readOnly 오버라이드 |

세 문제 모두 같은 호출 경로(`/posts/search` → `PostService.searchPostsByRelevance` → 4개 native query)를 따라가며 한 패치로 묶어서 고쳤다.

---

## 1. 문제 1 — `similarity()` 술어가 GIN 인덱스를 못 탄다

### 1.1 증상

V25 마이그레이션에서 다음과 같이 `gin_trgm_ops` 인덱스를 만들어 두었다.

```sql
CREATE INDEX IF NOT EXISTS idx_therapy_posts_search_text_trgm
    ON therapy_posts USING GIN (search_text gin_trgm_ops);
```

기대: `similarity(p.search_text, :keyword) > 0.03` 술어가 이 인덱스를 사용해 후보 행을 빠르게 좁혀줄 것.

실제: `EXPLAIN ANALYZE` 로 보면 `Seq Scan on therapy_posts` 가 뜨고 인덱스가 사용되지 않는다. 데이터가 많아질수록 직선적으로 느려진다.

### 1.2 근본 원인

PostgreSQL `pg_trgm` 의 `gin_trgm_ops` 는 **연산자 클래스**다. 이게 인덱스로 가속할 수 있는 건 다음 연산자들이다.

- `%` (similarity 매칭, 임계값은 `pg_trgm.similarity_threshold` GUC 가 결정)
- `<%` , `%>` (word similarity)
- `LIKE` / `ILIKE` (와일드카드 substring)
- `=`, `<>`

`similarity(a, b)` 함수 호출 + `> 0.03` 비교는 위 목록에 없다. PG 플래너는 함수 결과에 대한 비교 술어로부터 인덱스 키를 추출하는 일반화된 메커니즘이 없기 때문에, "이 함수가 사실상 trigram 매칭과 동치다" 는 걸 알지 못한다 → 풀스캔 + 행마다 함수 호출.

기존 쿼리에는 `OR` 의 다른 가지로 `ILIKE '%' || :escapedKeyword || '%'` 가 있어서 거기서는 인덱스가 일부 동작하지만, **OR 의 한 가지가 풀스캔이면 BitmapOr 를 못 만들거나 효과가 깨진다.**

### 1.3 수정 — `%` 연산자 + `SET LOCAL` 임계값

핵심 아이디어:

> `similarity(...) > 임계값` 은 정확히 `text % text` 연산자가 의미하는 바다. `%` 의 임계값은 세션/트랜잭션 GUC 인 `pg_trgm.similarity_threshold` 가 결정한다.

따라서 술어를 다음과 같이 교체했다.

```sql
-- before
similarity(p.search_text, :keyword) > 0.03

-- after
p.search_text % :keyword
```

그리고 임계값은 native query 호출 직전에 트랜잭션 스코프로 0.03 으로 낮춘다.

`PostService.findPostsByRelevance` 안에서:

```java
// pg_trgm % 연산자의 임계값을 트랜잭션 스코프로 0.03 으로 낮춘다.
// 트랜잭션 종료 시 자동으로 원복된다.
entityManager.createNativeQuery("SET LOCAL pg_trgm.similarity_threshold = 0.03")
        .executeUpdate();
```

`EntityManager` 는 필드 주입으로 받았다.

```java
// RELEVANCE 검색에서 SET LOCAL pg_trgm.similarity_threshold 를 실행하기 위한 EntityManager.
// @RequiredArgsConstructor 가 생성자에 포함하지 않도록 final 을 붙이지 않는다.
@PersistenceContext
private EntityManager entityManager;
```

> ⚠️ `final` 을 일부러 빼는 트릭에 주의. `PostService` 클래스는 `@RequiredArgsConstructor` 를 쓰는데, 이게 생성자에 final 필드를 자동으로 넣는다. EntityManager 는 `@PersistenceContext` 컨테이너 주입이라 생성자 인자에 들어가면 안 된다 → final 을 빼서 lombok 이 무시하도록 한다.

### 1.4 왜 `SET LOCAL` 인가 (`SET` 이나 함수 호출과의 차이)

| 옵션 | 스코프 | 트랜잭션 종료 시 | 평가 |
|------|--------|-------------------|------|
| `SET pg_trgm.similarity_threshold = 0.03` | 세션 전체 | **유지됨** (롤백 시에도) | ❌ 다른 쿼리에 영향, 커넥션 풀에서 위험 |
| `SET LOCAL pg_trgm.similarity_threshold = 0.03` | 현재 트랜잭션 | **자동 해제** | ✅ 안전, 격리 보장 |
| `set_limit(0.03)` 함수 | 세션 전체 | 유지됨 | ❌ `SET` 과 동일한 위험 |

커넥션 풀 환경에서는 세션 스코프 변경이 다른 요청에 새어 나가면 안 되므로 `SET LOCAL` 이 정답이다. 트랜잭션 끝나면 자동으로 원래 값(default 0.3)으로 돌아가므로 `RESET` 호출도 불필요하다.

### 1.5 4개 native query 모두 동일하게 수정

`TherapyPostRepository.java` 의 4개 메서드 각각의 매칭 술어를 동일하게 교체했다.

```sql
-- (a) (b) (c) (d) 모두 공통
AND (
      p.search_text % :keyword
   OR p.search_text ILIKE '%' || :escapedKeyword || '%' ESCAPE '\\'
)
```

이렇게 하면:

- `%` 가지: GIN trigram 인덱스를 직접 사용해 후보 추출
- `ILIKE` 가지: 와일드카드 패턴이라 PG 가 trigram 추출을 시도해 마찬가지로 GIN 인덱스를 사용
- 두 가지가 OR 로 묶이면 PG 플래너가 **BitmapOr** 로 두 인덱스 결과를 합쳐 후보를 좁힌다

ILIKE fallback 을 그대로 둔 이유: 한국어 짧은 키워드(2글자 등)는 trigram similarity 가 0 에 가까워 `%` 매칭에서도 누락될 수 있다. ILIKE substring 매칭은 그런 경우의 안전망이다. (메모리 노트 `feedback_korean_trigram_search.md` 에 기록된 회귀 시나리오와 일치.)

### 1.6 주석으로 의도 박제

리포지터리 상단에 큰 주석 블록을 추가해 "왜 이렇게 짰는지" 를 박아 두었다 — 다음에 누군가 "그냥 `similarity() > 0.03` 으로 되돌리면 더 직관적이지 않나?" 라고 생각하지 않도록.

```java
// RELEVANCE 검색 — pg_trgm % 연산자 + ILIKE fallback, 커서 기반 무한스크롤 전용.
//
// 주요 설계:
// - 매칭 술어: `search_text % :keyword` (gin_trgm_ops 가 직접 지원하는 연산자) 와
//   `ILIKE` 양쪽을 OR 로 묶는다. 둘 다 idx_therapy_posts_search_text_trgm GIN 인덱스를
//   사용해 BitmapOr 로 후보를 좁힌다. 임계값은 호출자가 SET LOCAL pg_trgm.similarity_threshold
//   로 미리 지정한다 (현재 0.03). similarity(...) > 0.03 함수 호출 형태는 GIN 인덱스를
//   못 타기 때문에 % 연산자로 교체했다.
// - 점수 컬럼: similarity() 결과(real/float4) 를 numeric(10,8) 로 캐스트해 노출한다.
//   응답 BigDecimal → 클라이언트 → 다음 요청 BigDecimal 왕복에서 정밀도 손실이 없어
//   동등 비교(=) 가 안전하다.
// - 커서 조건: 동일하게 numeric(10,8) 캐스트한 값과 :lastScore 를 비교한다.
// - LIMIT 은 :limit 파라미터로 받아 hasNext 판별용 take+1 조회를 수행한다.
```

---

## 2. 문제 2 — float round-trip 정밀도 손실

### 2.1 증상 (잠재)

이 문제는 운 좋으면 발생하지 않고, 발생해도 매우 산발적이라 디버깅이 어렵다. 시나리오:

1. 첫 페이지 native 쿼리가 마지막 행의 `similarity()` 를 `0.12345679` (float4) 로 계산.
2. JDBC 드라이버가 이걸 Java `double` 로 받아 `0.12345679...` (double 정밀도로 약간 확장된 값).
3. JSON 직렬화가 `0.12345679` 같은 문자열로 내려감.
4. 클라이언트가 그대로 다음 요청에 실어 보냄.
5. 서버가 받은 `Double` 을 `:lastScore` 로 native 쿼리에 바인딩.
6. PG 가 비교: `similarity(p.search_text, :keyword) = :lastScore`
7. 좌변은 다시 row 마다 새로 계산된 float4. 우변은 double 로 들어온 값을 바인딩 시 PG 가 어떤 타입으로 추론하는지에 따라 약간 다른 값이 될 수 있다.
8. 좌우가 1 ULP 라도 어긋나면 동등 비교 false → **그 행은 다음 페이지에 등장하지 않는다 (건너뛴 것)**.
9. 또는 같은 점수의 다른 행이 `<` 분기에서 살아남아 **이전 페이지의 마지막 행과 함께 또 등장한다 (중복)**.

복합 커서 정렬 SQL 의 동등 비교 부분이 정확히 이 위험에 노출돼 있다.

```sql
similarity(...) < :lastScore
OR (similarity(...) = :lastScore AND p.id < :lastId)  -- ← 여기
```

### 2.2 근본 원인

- PG `similarity()` 의 반환 타입은 **`real` (float4, 32-bit IEEE 754)**.
- Java `double` 은 64-bit 라 더 정밀하지만, **float4 → double 변환 자체에 손실이 있을 수 있다** (모든 float4 값이 같은 비트 패턴의 double 로 표현되는 것은 아니므로).
- 더 큰 문제는 JDBC/Hibernate 가 `Double` 파라미터를 PG 에 다시 보낼 때 어떤 타입으로 바인딩하느냐다. `double precision` 으로 바인딩되면 PG 는 비교 시 **좌변 float4 를 double precision 으로 promote** 한 뒤 비교한다. 그런데 같은 텍스트로 다시 계산한 좌변 float4 → double 과, 클라이언트를 거쳐 들어온 우변 double 이 비트 단위로 같으리란 보장이 없다.

요약하면 **부동소수점 컬럼을 동등 비교 키(커서) 로 쓰는 것 자체가 위험하다.** keyset pagination 의 키 컬럼은 정확한 동등 비교가 가능한 타입(정수, decimal, timestamp 등) 이어야 한다.

### 2.3 수정 — `numeric(10,8)` 캐스트 + `BigDecimal` 통일

해결 방향: **점수를 PG 안에서 미리 정확한 십진 표현(`numeric`)으로 캐스트** 한 뒤 이 값을 그대로 클라이언트와 왕복시킨다. Java 쪽은 `BigDecimal` 로 받아 정밀도 손실 없이 다시 SQL 에 전달한다.

#### (a) SQL — SELECT 절에서 캐스트

4개 native query 모두 동일 패턴.

```sql
-- before
SELECT p.id, similarity(p.search_text, :keyword) AS score

-- after
SELECT p.id, CAST(similarity(p.search_text, :keyword) AS numeric(10,8)) AS score
```

`numeric(10,8)`: 전체 자릿수 10, 소수점 이하 8자리. similarity 값은 0~1 사이라 소수점 이하만 의미 있고 8자리면 충분히 안전한 해상도다.

#### (b) SQL — 커서 비교에서도 캐스트

같은 캐스트를 WHERE 절의 비교에서도 적용해 좌우 타입을 일치시킨다.

```sql
-- before
similarity(p.search_text, :keyword) < :lastScore
OR (similarity(p.search_text, :keyword) = :lastScore AND p.id < :lastId)

-- after
CAST(similarity(p.search_text, :keyword) AS numeric(10,8)) < :lastScore
OR (CAST(similarity(p.search_text, :keyword) AS numeric(10,8)) = :lastScore AND p.id < :lastId)
```

`:lastScore` 는 Java 쪽에서 BigDecimal 로 들어오므로 PG 가 자연스럽게 `numeric` 으로 바인딩하고, 좌변도 같은 numeric 이라 비교가 정확하다.

#### (c) Repository 메서드 시그니처

```java
// before
@Param("lastScore") double lastScore,

// after
@Param("lastScore") BigDecimal lastScore,
```

(b), (d) 두 메서드 모두.

#### (d) Service — 다음 커서 추출

```java
// before
Double nextScore = null;
// ...
nextScore = ((Number) lastRow[1]).doubleValue();

// after
// score 는 SQL 에서 numeric(10,8) 로 캐스트되어 BigDecimal 로 그대로 전달된다.
BigDecimal nextScore = null;
// ...
nextScore = (BigDecimal) lastRow[1];
```

JDBC 가 PG `numeric` 컬럼을 `java.math.BigDecimal` 로 매핑하므로 캐스트 한 줄로 끝난다. `Number.doubleValue()` 같은 변환은 다시 정밀도 손실을 만드므로 절대 쓰지 않는다.

#### (e) Service 메서드 시그니처

```java
// before
public SearchCursorResponse searchPostsByRelevance(
        PostSearchCondition condition,
        Double lastScore,
        Long lastId,
        int size,
        UserRole role
)

// after
public SearchCursorResponse searchPostsByRelevance(
        PostSearchCondition condition,
        BigDecimal lastScore,   // ← 변경
        Long lastId,
        int size,
        UserRole role
)
```

내부 `findPostsByRelevance` 도 동일하게 `BigDecimal lastScore` 로 받는다.

#### (f) DTO

```java
// before
public static class SearchCursorMeta {
    private final boolean hasNextData;
    private final Double nextScore;
    private final Long nextId;
}

// after
public static class SearchCursorMeta {
    private final boolean hasNextData;
    private final BigDecimal nextScore;
    private final Long nextId;
}
```

DTO 클래스 헤더 javadoc 에 의도를 박제:

```java
/**
 * ...
 * nextScore 가 BigDecimal 인 이유: pg_trgm similarity 결과는 PG 측에서 real(float4) 인데
 * 클라이언트 왕복 시 부동소수 round-trip 정밀도 손실로 동등 비교(=)가 빗나갈 수 있다.
 * Repository 쿼리에서 numeric(10,8) 로 캐스트한 값을 그대로 받아 BigDecimal 로 노출한다.
 */
```

#### (g) Controller

```java
// before
@RequestParam(required = false) Double lastScore,

// after
@RequestParam(required = false) BigDecimal lastScore,
```

Spring MVC 는 BigDecimal 쿼리 파라미터 변환을 기본 지원하므로 추가 컨버터 없이 동작한다.

### 2.4 클라이언트 영향

JSON 표현은 그대로 숫자 리터럴이다 (`"nextScore": 0.12345679`). 클라이언트는 받은 값을 가공 없이 다음 요청에 실어 보내기만 하면 된다. 클라이언트 측이 자체적으로 float 으로 파싱했다가 다시 직렬화하면 같은 정밀도 문제가 재발할 수 있으므로, 가능하면 **문자열로 그대로 보존하라고 안내**해야 한다.

> 후속 작업 후보: API 문서/`FRONTEND_BREAKING_CHANGES.md` 에 "nextScore 는 문자열로 보존하라" 가이드 추가.

### 2.5 BigDecimal 동등 비교의 함정 (하지 않은 것)

`BigDecimal` 자체는 Java `equals()` 가 scale 까지 본다 — `new BigDecimal("0.10").equals(new BigDecimal("0.1")) == false`. 다만 이 비교는 SQL 안에서 PG 의 `numeric = numeric` 으로만 일어나므로(좌우 모두 `numeric(10,8)` 으로 캐스트) 같은 scale 을 갖게 되어 안전하다. Java 쪽에서 BigDecimal 끼리 직접 동등 비교를 하는 코드는 추가하지 않았다.

---

## 3. 문제 3 — readOnly 트랜잭션에서 `SET LOCAL` 위험

### 3.1 증상 (잠재)

문제 1의 수정에서 `entityManager.createNativeQuery("SET LOCAL pg_trgm.similarity_threshold = 0.03").executeUpdate()` 를 도입했다. 이게 `PostService` 의 클래스 레벨 `@Transactional(readOnly = true)` 안에서 실행된다.

- PostgreSQL 자체는 READ ONLY 트랜잭션에서도 `SET LOCAL` 을 허용한다 (서버 GUC 변경은 데이터 변경이 아니므로).
- 그러나 일부 JDBC 드라이버/Hibernate 버전에서 readOnly 힌트와 `executeUpdate()` 호출의 조합이 `Connection.setReadOnly(true)` 를 따라 거부되는 사례가 보고된 적이 있다.
- 또한 `executeUpdate()` 는 의미상 "데이터 변경" 으로 간주될 여지가 있어 어떤 인터셉터/모니터링 도구가 거부할 수 있다.

확실히 동작하리란 보장이 없는 영역이라 안전 차원에서 readOnly 를 푼다.

### 3.2 수정 — 메서드 단위 `@Transactional` 오버라이드

```java
// before — 클래스 레벨 @Transactional(readOnly = true) 만 적용됨
public SearchCursorResponse searchPostsByRelevance(
        PostSearchCondition condition,
        BigDecimal lastScore,
        Long lastId,
        int size,
        UserRole role
)

// after — 메서드에 명시적으로 @Transactional 추가
@Transactional
public SearchCursorResponse searchPostsByRelevance(
        PostSearchCondition condition,
        BigDecimal lastScore,
        Long lastId,
        int size,
        UserRole role
)
```

Spring `@Transactional` 의 메서드 단위 어노테이션은 클래스 단위 설정을 오버라이드하므로 `readOnly` 가 기본값 `false` 로 풀린다.

코드 위 javadoc 에 이유를 명시:

```java
/**
 * RELEVANCE 검색 (무한스크롤) — 외부 진입 메서드.
 * lastScore/lastId 가 모두 null 이면 첫 페이지, 모두 있으면 다음 페이지.
 * 컨트롤러에서 두 값의 쌍 검증을 통과한 뒤 호출되는 것을 가정한다.
 *
 * 클래스 레벨 readOnly=true 를 명시적으로 오버라이드해 readOnly=false 트랜잭션을 연다.
 * 그 안에서 SET LOCAL pg_trgm.similarity_threshold 를 안전하게 실행하기 위함이다
 * (PG READ ONLY 트랜잭션에서도 SET LOCAL 자체는 허용되지만, JDBC/Hibernate 일부
 *  버전에서 거부된 사례가 보고되어 안전 차원에서 명시적으로 풀어둔다).
 * SET LOCAL 은 트랜잭션 종료 시 자동 해제되므로 RESET 호출은 불필요하다.
 */
```

### 3.3 트랜잭션 범위와 SET LOCAL 의 자동 원복

`SET LOCAL` 의 효과는 **현재 트랜잭션이 끝나면 자동으로 원래 값으로 되돌아간다** (commit/rollback 무관). 따라서:

- `RESET pg_trgm.similarity_threshold` 같은 명시적 원복 호출이 필요 없다.
- 같은 커넥션이 풀에 반환되어 다음 요청이 가져가도 GUC 가 새어 나가지 않는다.
- 트랜잭션 안에서 다른 native 쿼리가 실행되는 동안에만 0.03 이 적용된다.

이것이 `SET` 이 아닌 `SET LOCAL` 을 쓴 결정적 이유 (1.4 와 동일).

---

## 4. 변경 파일 요약

| 파일 | 변경 요지 |
|------|-----------|
| `repository/TherapyPostRepository.java` | 4개 native query: `similarity() > 0.03` → `% :keyword`, SELECT 절 `numeric(10,8)` 캐스트, 커서 비교에도 동일 캐스트, `lastScore` 파라미터 타입 `double` → `BigDecimal`. 상단에 설계 의도 주석 추가. |
| `service/PostService.java` | `searchPostsByRelevance` / `findPostsByRelevance` 의 `lastScore` 타입 `Double` → `BigDecimal`. 메서드 단위 `@Transactional` 추가. `@PersistenceContext EntityManager` 주입. native query 호출 직전 `SET LOCAL pg_trgm.similarity_threshold = 0.03` 실행. 다음 커서 추출 시 `(BigDecimal) lastRow[1]` 로 직접 캐스트. |
| `controller/PostController.java` | `/posts/search` 의 `lastScore` 쿼리 파라미터 타입 `Double` → `BigDecimal`. |
| `dto/SearchCursorResponse.java` | `nextScore` 필드 타입 `Double` → `BigDecimal`. javadoc 에 변경 이유 박제. |

---

## 5. 검증 시나리오 (수동 체크리스트)

수정 후 동작을 확인할 때 돌려볼 시나리오들.

### 5.1 인덱스 사용 확인

```sql
EXPLAIN ANALYZE
SELECT p.id, CAST(similarity(p.search_text, '감각통합') AS numeric(10,8)) AS score
FROM therapy_posts p
WHERE p.deleted_at IS NULL
  AND (
        p.search_text % '감각통합'
     OR p.search_text ILIKE '%감각통합%' ESCAPE '\\'
  )
ORDER BY score DESC, p.id DESC
LIMIT 11;
```

- 실행 전에 한 트랜잭션 안에서 `SET LOCAL pg_trgm.similarity_threshold = 0.03;` 를 먼저 실행하거나, 세션에서 `SET pg_trgm.similarity_threshold = 0.03;` 으로 임시 설정.
- `EXPLAIN` 결과에 `Bitmap Index Scan on idx_therapy_posts_search_text_trgm` 또는 `BitmapOr` 가 보이면 OK. `Seq Scan` 만 보이면 회귀.

### 5.2 페이지네이션 무결성

- 키워드 하나로 size=5 무한스크롤 5페이지를 끝까지 페이징.
- 등장한 게시글 ID 들을 모아서 **중복이 없고**, 마지막 페이지 이전까지는 **건너뛰는 ID 가 없는지** 확인.
- 같은 키워드로 size=10 한 번, size=5 두 번 호출 결과를 합쳐 비교 → 같은 게시글 셋이어야 한다.

### 5.3 짧은 한국어 키워드 회귀 방지

- `"통합"`, `"치료"` 같은 2글자 키워드로 검색 → ILIKE fallback 가지가 동작해 결과가 비지 않아야 한다.
- 메모리 노트 `feedback_korean_trigram_search.md` 와 일치하는 동작.

### 5.4 BigDecimal 왕복

- 첫 페이지 응답의 `meta.nextScore` 값을 그대로 (가공 없이) `lastScore` 쿼리 파라미터로 다음 요청에 전달.
- 다음 페이지 결과가 첫 페이지의 마지막 행과 정확히 인접하는지 (중복 없음, 누락 없음) 확인.
- Swagger UI 에서 직접 입력해 보는 경우, `0.12345679` 같은 값을 그대로 텍스트로 복붙해야 한다 (브라우저 number input 이 자체 변환하는 위험 회피).

### 5.5 readOnly 오버라이드

- 검색 호출 시 `org.springframework.transaction.TransactionSystemException` 같은 예외가 안 떠야 한다.
- 디버그 로그에서 트랜잭션 시작이 `readOnly=false` 로 열리는지 확인 (`spring.jpa.show-sql`, `logging.level.org.springframework.transaction=DEBUG`).

---

## 6. 학습 포인트

이 트러블슈팅에서 다시 쓸 수 있는 일반화 가능한 교훈들.

### 6.1 인덱스를 타려면 술어가 인덱스 연산자 클래스가 지원하는 형태여야 한다

함수 호출 + 비교 (`f(col) > x`) 는 일반적으로 인덱스를 못 탄다. 이걸 인덱스에 태우려면:

1. 같은 의미의 **연산자**가 있다면 그걸 쓴다 (`%`, `<%`, `@@` 등).
2. 또는 **함수 인덱스** (`CREATE INDEX ... ON t (f(col))`) 를 만든다.
3. 또는 **표현식 인덱스를 매칭하도록 쿼리를 다시 쓴다**.

`gin_trgm_ops` 의 경우 1번이 답이었다.

### 6.2 부동소수 컬럼은 keyset pagination 의 키로 쓰면 위험하다

- float4/float8 은 동등 비교가 깨질 수 있다.
- 해결책:
  - `numeric` 으로 캐스트해 십진 표현으로 고정.
  - 또는 점수를 정수로 변환 (`ROUND(score * 100000)::int` 같은 식).
  - 또는 점수를 커서에서 빼고 `(id)` 만 보조 키로 쓰되 정렬 순서를 다른 안정 키로 보강.

이번엔 첫 번째(numeric 캐스트) 를 선택했다 — SQL 표현이 가장 직관적이고 클라이언트 인터페이스도 BigDecimal 로 깔끔하다.

### 6.3 GUC 변경은 무조건 `SET LOCAL`

커넥션 풀 환경에서 세션 GUC 를 건드리는 건 누수 위험이 매우 크다. 기본은 `SET LOCAL` 이고, 트랜잭션 범위가 너무 좁아 안 되는 경우에만 명시적으로 다른 옵션을 검토.

### 6.4 클래스 레벨 readOnly 가 있는 곳에서 서버 상태 변경 SQL 을 호출할 땐 메서드 단위 오버라이드

- `SET LOCAL`, advisory lock, 임시 테이블 등 "데이터 변경은 아니지만 서버 상태를 건드리는" 호출.
- 의도가 readOnly 어노테이션과 충돌할 수 있고, 일부 드라이버/툴에서 거부된다.
- `@Transactional` 한 줄을 메서드에 박는 게 비용 대비 안전.

### 6.5 결정 의도를 코드 옆 주석에 남겨라

이번에 추가한 두 종류 주석:

1. 리포지터리 상단의 "이렇게 짠 이유" 블록 — 다음 사람이 `similarity() > 0.03` 으로 되돌리지 않도록.
2. 서비스 메서드 javadoc 의 "왜 readOnly 를 풀었는지" — 다음 사람이 `@Transactional` 을 무심코 지우지 않도록.

미래의 자신이 회귀를 막는 가장 싼 도구는 코드 옆 주석이다. 아키텍처 문서는 잘 안 읽힌다.

---

## 7. 후속 과제 (아직 안 한 것들)

이 트러블슈팅에서 다루지 않은 항목들 — 별도 작업 후보.

- **테스트 추가**: 짧은 한국어 키워드, BigDecimal 커서 왕복, 마지막 페이지 경계, 빈 결과 등 시나리오의 통합 테스트.
- **EXPLAIN 자동 회귀 가드**: CI 에서 검색 쿼리가 `Seq Scan` 으로 떨어지면 실패하는 회귀 테스트 (Postgres 전용).
- **임계값 0.03 의 운영 튜닝**: 데이터가 쌓이면 0.03 이 너무 낮아 노이즈가 늘 수 있다. 임계값을 application property 로 빼서 조정 가능하게 만드는 안.
- **Frontend 가이드**: `nextScore` 를 문자열로 보존하라는 안내를 `FRONTEND_BREAKING_CHANGES.md` 또는 API 스펙에 추가.
- **`PostSearchCondition` 키워드 정규화**: 양 끝 trim 외에 NFC 정규화 등 한국어 정규화도 고려할지 검토.
