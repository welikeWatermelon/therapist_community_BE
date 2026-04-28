# Commit 2: 검색어 길이 제한 + size 범위 검증

## 무엇을 했나

`/api/v1/posts/search` 엔드포인트에 입력 검증을 추가했다:
- keyword: 공백 제거 후 **2~100자** (미충족 시 400 Bad Request)
- size: **1~50** (미충족 시 400 Bad Request)

## 왜 했나

**보안/성능 방어:**
- 1글자 검색어: pg_trgm은 trigram(3글자 단위)으로 동작 → 2글자 미만은 인덱스 무의미
- 수천 자 검색어: `similarity()` 함수의 계산 비용이 입력 길이에 비례 → DoS 벡터
- size 제한 없음: `size=10000` 같은 요청으로 대량 데이터 반환 → 메모리/네트워크 부하

## 핵심 개념 학습

### 1. 입력 검증: @Validated vs 수동 검증

**Spring에서 @RequestParam 검증하는 2가지 방법:**

#### 방법 A: @Validated + Bean Validation 어노테이션
```java
@Validated  // 클래스 레벨
@RestController
public class PostController {
    public ResponseEntity<?> search(
        @RequestParam @NotBlank @Size(min=2, max=100) String keyword,
        @RequestParam @Min(1) @Max(50) int size
    ) { ... }
}
```

- `ConstraintViolationException` 발생
- MockMvc standalone 모드에서 **동작하지 않음** (Spring 컨텍스트 필요)
- 테스트하려면 `@SpringBootTest` + `@AutoConfigureMockMvc` 필요

#### 방법 B: 수동 검증 (우리가 선택한 방식)
```java
if (keyword == null || keyword.isBlank() || keyword.trim().length() < 2 || keyword.trim().length() > 100) {
    throw new CustomException(ErrorCode.INVALID_INPUT);
}
```

- 기존 `CustomException` + `ErrorResponse` 체계와 자연스럽게 통합
- MockMvc standalone에서도 바로 테스트 가능
- 프레임워크 마법 없이 로직이 명확히 보임

**왜 수동 검증을 선택했나:**
1. 기존 프로젝트가 `CustomException(ErrorCode.INVALID_INPUT)` 패턴을 쓰고 있음
2. `@Validated`는 `ConstraintViolationException`을 발생시켜 응답 형식이 다를 수 있음
3. 테스트가 더 단순 (Spring 컨텍스트 불필요)
4. `@RequestParam`의 `@NotBlank`은 의외로 함정이 많음 (빈 문자열 vs null 등)

### 2. GlobalExceptionHandler에 추가한 것

```java
@ExceptionHandler(ConstraintViolationException.class)
public ResponseEntity<ErrorResponse> handleConstraintViolation(...) {
    return ResponseEntity
            .status(ErrorCode.INVALID_INPUT.getStatus())
            .body(new ErrorResponse(ErrorCode.INVALID_INPUT));
}
```

향후 다른 컨트롤러에서 `@Validated`를 사용할 경우를 대비해 남겨둠.
없으면 500 Internal Server Error로 떨어짐 → 사용자 경험 최악.

### 3. 검증 순서의 중요성

```java
// 1. keyword 검증 (가장 먼저)
if (keyword == null || keyword.isBlank() || keyword.trim().length() < 2 || ...) { ... }

// 2. size 검증
if (size < 1 || size > 50) { ... }

// 3. 커서 쌍 검증 (기존)
if ((lastScore == null) != (lastId == null)) { ... }

// 4. 비즈니스 로직 실행
PostSearchCondition condition = new PostSearchCondition(keyword, therapyArea, postType);
```

**원칙:** 값싼 검증을 먼저, 비싼 연산(DB 쿼리)은 나중에.
잘못된 입력이면 DB에 가기도 전에 빠르게 거부.

### 4. trigram과 검색어 길이의 관계

**pg_trgm 동작 원리:**
```
"감각통합" → trigram 분해:
  "  감", " 감각", "감각통", "각통합", "통합 ", "합  "
```

- 3글자 단위로 쪼개서 비교
- **1글자 검색어**: trigram을 만들 수 없음 → `%` 연산자가 사실상 무의미
- **2글자 검색어**: 최소 1개 trigram 생성 가능 → 매우 낮은 정밀도지만 ILIKE fallback으로 커버

**100자 제한 근거:**
- `similarity()` 함수는 두 문자열의 trigram 집합을 비교
- 입력 길이 N → trigram 수 ≈ N개 → 비교 연산 O(N*M)
- 100자면 충분히 의미 있는 검색이 가능하면서도 연산 비용 제한

### 5. MockMvc 테스트 구조

```java
mockMvc = MockMvcBuilders.standaloneSetup(postController)
        .setCustomArgumentResolvers(authResolver)    // @AuthenticationPrincipal 처리
        .setControllerAdvice(new GlobalExceptionHandler())  // 예외 핸들링
        .build();
```

**`setControllerAdvice` 왜 필요한가:**
`CustomException` → `GlobalExceptionHandler` → 400 응답 변환이 되려면
MockMvc에 핸들러를 등록해야 한다. 안 하면 예외가 그대로 throw됨.

## 파일 변경 목록

| 파일 | 변경 |
|------|------|
| `PostController.java` | keyword/size 수동 검증 추가 |
| `GlobalExceptionHandler.java` | ConstraintViolationException 핸들러 추가 |
| `PostSearchValidationTest.java` | 7개 검증 테스트 신규 |

## 테스트 케이스 설계 근거

| 케이스 | 입력 | 왜 테스트하나 |
|--------|------|--------------|
| 1글자 | `"a"` | trigram 불가, 최소 2글자 확인 |
| 빈문자열 | `""` | Spring이 null로 처리할 수도 있음 |
| 공백만 | `"   "` | `isBlank()` 처리 확인 |
| 101자 | `"가" × 101` | 경계값 (100은 OK, 101은 NG) |
| size 0 | `0` | 최소값 경계 |
| size 51 | `51` | 최대값 경계 (50은 OK) |
| 정상 | `"감각통합"`, size=10 | 해피 패스 |
