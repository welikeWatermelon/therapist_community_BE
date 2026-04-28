# Commit 1: 검색 접근 로깅 AOP 구현

## 무엇을 했나

검색 API(`/api/v1/posts/search`)가 호출될 때마다 자동으로 JSON 로그를 남기는 시스템을 구축했다.

## 왜 했나

- 현재 사용자들이 **어떤 방식으로** 검색하는지 데이터가 전혀 없음
- pgvector(의미 검색) 도입 여부를 **감이 아닌 데이터**로 결정하기 위해
- 1주일간 로그를 쌓고 분석한 후 다음 단계 결정

## 핵심 개념 학습

### 1. Spring AOP (Aspect-Oriented Programming)

**AOP란?**
핵심 비즈니스 로직(검색)을 건드리지 않고, 부가 관심사(로깅)를 "횡단"으로 끼워넣는 기법.

```
[요청] → [AOP: 시간 측정 시작] → [Controller.searchPosts()] → [AOP: 로그 기록] → [응답]
```

**왜 AOP를 선택했나? (vs Interceptor vs Filter)**

| 방식 | 파라미터 접근 | 반환값 접근 | 타입 안전 |
|------|:---:|:---:|:---:|
| AOP (@Around) | ✅ 직접 | ✅ 직접 | ✅ |
| HandlerInterceptor | ⚠️ HttpServletRequest에서 파싱 | ❌ 어려움 | ❌ |
| Filter | ❌ 바이트 스트림 | ❌ 바이트 스트림 | ❌ |

AOP는 `keyword`, `therapyArea` 같은 **Java 타입 파라미터**를 그대로 접근할 수 있고,
반환값(`ResponseEntity<ApiResponse<SearchCursorResponse>>`)에서 결과 건수도 바로 추출 가능.

**핵심 어노테이션:**
```java
@Aspect        // 이 클래스가 AOP 관점임을 선언
@Component     // Spring 빈으로 등록
@Around("execution(* ...PostController.searchPosts(..))")
//       ↑ 대상 메서드를 감싸서 전/후 모두 제어
```

**@Around가 동작하는 방식:**
```java
public Object logSearchAccess(ProceedingJoinPoint joinPoint) throws Throwable {
    // --- 메서드 실행 전 ---
    StopWatch stopWatch = new StopWatch();
    stopWatch.start();
    
    Object result = joinPoint.proceed();  // ← 실제 searchPosts() 실행
    
    // --- 메서드 실행 후 ---
    stopWatch.stop();
    log.info(json);
    return result;  // 원래 응답 그대로 반환
}
```

### 2. Logback 비동기 로깅

**문제:** 파일 I/O는 느림. 검색 응답 시간에 영향을 줄 수 있음.

**해결:** AsyncAppender — 로그를 큐에 넣고, 별도 스레드가 파일에 쓴다.

```
[요청 스레드] → log.info(json) → [큐에 넣기만 하고 바로 반환]
                                        ↓ (별도 스레드)
                                  [파일에 실제 쓰기]
```

**logback-spring.xml 구조:**
```xml
<!-- 1. 실제로 파일에 쓰는 appender -->
<appender name="SEARCH_ACCESS_FILE" class="RollingFileAppender">
    <file>logs/search-access.log</file>
    <rollingPolicy>  <!-- 매일 새 파일, 30일 보관 -->
    <encoder><pattern>%msg%n</pattern></encoder>  <!-- JSON만 출력 -->
</appender>

<!-- 2. 비동기 래퍼 -->
<appender name="SEARCH_ACCESS" class="AsyncAppender">
    <appender-ref ref="SEARCH_ACCESS_FILE"/>
    <queueSize>1024</queueSize>           <!-- 큐 크기 -->
    <discardingThreshold>0</discardingThreshold>  <!-- 0 = 절대 버리지 않음 -->
</appender>

<!-- 3. 로거 등록 (일반 로그와 분리) -->
<logger name="SEARCH_ACCESS" level="INFO" additivity="false">
```

**`additivity="false"`의 의미:**
이 로거의 메시지가 상위(root) 로거로 전파되지 않음.
→ 콘솔에 검색 로그가 중복 출력되지 않음.

### 3. @JsonInclude(NON_NULL)

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchAccessLog {
    private final String therapyArea;   // null이면 JSON에서 아예 생략
    private final String error;         // 정상이면 생략, 에러 시만 포함
}
```

**왜?** 로그 파일 크기 절약 + 파싱 시 noise 감소

### 4. boolean 필드의 Jackson 직렬화 함정

```java
private final boolean isZeroResult;
```

Lombok `@Getter`가 만드는 메서드: `isZeroResult()` (is 접두어 유지)
Jackson이 직렬화할 때: `"zeroResult"` (is를 제거!)

**해결:**
```java
@JsonProperty("isZeroResult")
private final boolean isZeroResult;
```

### 5. 의존성 추가: spring-boot-starter-aop

Spring Boot에서 AOP를 쓰려면 이 starter가 필요.
내부적으로 AspectJ weaver + Spring AOP 자동 설정 포함.

## 파일 구조

```
src/main/java/.../global/logging/
├── SearchAccessLog.java       ← 로그 DTO (불변 객체, @Builder)
└── SearchAccessLogger.java    ← AOP Aspect

src/main/resources/
└── logback-spring.xml         ← 비동기 파일 로깅 설정

src/test/java/.../global/logging/
└── SearchAccessLoggerTest.java ← 4개 테스트 케이스
```

## 테스트 전략

AOP 테스트에서 실제 Spring 컨텍스트를 올리지 않고, `ProceedingJoinPoint`를 mock하여 단위 테스트로 처리.

**ListAppender 활용:**
```java
Logger logger = (Logger) LoggerFactory.getLogger("SEARCH_ACCESS");
ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
listAppender.start();
logger.addAppender(listAppender);

// ... AOP 실행 후 ...
String logMessage = listAppender.list.get(0).getFormattedMessage();
JsonNode json = objectMapper.readTree(logMessage);  // JSON 파싱하여 검증
```

이렇게 하면 실제 파일 I/O 없이도 로그 내용을 정확히 검증할 수 있다.

## 실제 로그 출력 예시

```json
{
  "timestamp": "2026-04-21T14:30:00+09:00",
  "requestId": "a1b2c3d4-...",
  "keyword": "감각통합",
  "keywordLength": 4,
  "therapyArea": "SENSORY_INTEGRATION",
  "userId": 1,
  "resultCount": 5,
  "responseTimeMs": 42,
  "isZeroResult": false
}
```
