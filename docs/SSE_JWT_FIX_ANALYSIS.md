# SSE eventCache 조기 삭제 수정 + JWT query param 토큰 경로 제한

## 수정 대상 파일

| 파일 | 변경 내용 |
|------|-----------|
| `SseEmitterRepository.java` | eventCache 생명주기 분리 + TTL 기반 정리 |
| `JwtAuthenticationFilter.java` | query param 토큰을 `/subscribe`에서만 허용 |
| `AsyncConfig.java` | `@EnableScheduling` 추가 |

---

## 1. SseEmitterRepository — eventCache 조기 삭제 수정

### 문제

`remove()`에서 마지막 emitter 제거 시 `eventCache`도 함께 삭제됨.
SSE 클라이언트가 재연결(3~5초 후)할 때 `Last-Event-ID`로 놓친 이벤트를 복구하려 하지만, 캐시가 이미 삭제되어 복구 불가.

### 수정 전

```java
public void remove(Long userId, String emitterId) {
    emitters.computeIfPresent(userId, (key, userEmitters) -> {
        userEmitters.remove(emitterId);
        if (userEmitters.isEmpty()) {
            eventCache.remove(userId);  // ← 캐시 조기 삭제
            return null;
        }
        return userEmitters;
    });
}
```

**흐름:**
```
클라이언트 연결 끊김
  → onCompletion/onTimeout 콜백
  → remove() 호출
  → 마지막 emitter → eventCache 삭제
  → 3~5초 후 클라이언트 재연결
  → Last-Event-ID로 캐시 조회
  → 캐시 없음 → 이벤트 유실
```

### 수정 후

```java
// remove() — emitter만 정리, eventCache는 유지
public void remove(Long userId, String emitterId) {
    emitters.computeIfPresent(userId, (key, userEmitters) -> {
        userEmitters.remove(emitterId);
        return userEmitters.isEmpty() ? null : userEmitters;
    });
}

// 5분마다 실행 — 30분 지난 캐시 엔트리 정리
@Scheduled(fixedRate = 5 * 60 * 1000)
public void cleanExpiredCache() {
    LocalDateTime expiry = LocalDateTime.now().minusMinutes(CACHE_TTL_MINUTES);
    eventCache.forEach((userId, queue) -> {
        queue.removeIf(event -> event.createdAt().isBefore(expiry));
        if (queue.isEmpty()) {
            eventCache.remove(userId, queue);
        }
    });
}
```

**흐름:**
```
클라이언트 연결 끊김
  → onCompletion/onTimeout 콜백
  → remove() 호출
  → emitter만 제거, eventCache는 유지
  → 3~5초 후 클라이언트 재연결
  → Last-Event-ID로 캐시 조회
  → 캐시 존재 → 놓친 이벤트 정상 복구
  → 30분 후 스케줄러가 만료된 캐시 자동 정리
```

### 이점

| 항목 | 수정 전 | 수정 후 |
|------|---------|---------|
| 재연결 시 이벤트 복구 | 불가능 (캐시 삭제됨) | 정상 동작 |
| 캐시 생명주기 | emitter에 종속 | TTL 기반 독립 관리 |
| 메모리 관리 | emitter 제거 시 즉시 해제 | 30분 TTL + 5분 주기 정리 (최대 50개/유저 제한은 유지) |

---

## 2. JwtAuthenticationFilter — query param 토큰 경로 제한

### 문제

`request.getParameter("token")`이 모든 엔드포인트에서 허용됨.
URL에 토큰이 포함되면 서버 access log, 브라우저 히스토리, Referer 헤더에 노출될 위험.
query param 토큰은 SSE(`EventSource`가 커스텀 헤더 미지원) 때문에 필요하므로 `/subscribe`에서만 허용해야 함.

### 수정 전

```java
if (authorization != null && authorization.startsWith("Bearer ")) {
    token = authorization.substring(7);
} else if (request.getParameter("token") != null) {  // ← 모든 경로에서 허용
    token = request.getParameter("token");
}
```

**토큰 노출 가능 경로:**
```
GET  /api/v1/posts?token=eyJ...              ← 허용됨
POST /api/v1/comments?token=eyJ...           ← 허용됨
GET  /api/v1/notifications/subscribe?token=eyJ... ← 허용됨
→ 모든 엔드포인트에서 URL에 토큰 노출 가능
```

### 수정 후

```java
private static final String SSE_SUBSCRIBE_PATH = "/api/v1/notifications/subscribe";

if (authorization != null && authorization.startsWith("Bearer ")) {
    token = authorization.substring(7);
} else if (isSubscribeEndpoint(request) && request.getParameter("token") != null) {
    token = request.getParameter("token");
}

private boolean isSubscribeEndpoint(HttpServletRequest request) {
    return SSE_SUBSCRIBE_PATH.equals(request.getRequestURI());
}
```

**토큰 노출 가능 경로:**
```
GET  /api/v1/posts?token=eyJ...              ← 무시됨 (헤더만 허용)
POST /api/v1/comments?token=eyJ...           ← 무시됨 (헤더만 허용)
GET  /api/v1/notifications/subscribe?token=eyJ... ← 허용됨 (SSE 전용)
→ /subscribe 1개 경로에서만 URL 토큰 허용
```

### 이점

| 항목 | 수정 전 | 수정 후 |
|------|---------|---------|
| query param 토큰 허용 범위 | 모든 엔드포인트 | `/subscribe`만 |
| 토큰 유출 위험 (로그/Referer) | 높음 (어디서든 URL에 토큰 가능) | 최소화 (SSE 전용 경로만) |
| SSE 기능 | 정상 | 정상 (영향 없음) |
| 일반 API 인증 | 헤더 + query param 둘 다 가능 | 헤더만 허용 (의도된 동작) |

---

## 3. AsyncConfig — @EnableScheduling 추가

`SseEmitterRepository.cleanExpiredCache()`의 `@Scheduled`가 동작하려면 `@EnableScheduling`이 필요.
기존 `@EnableAsync`와 같은 인프라 설정이므로 `AsyncConfig`에 추가.

### 수정 전

```java
@Configuration
@EnableAsync
public class AsyncConfig {
```

### 수정 후
@EnableScheduling은 Spring에 **"이 프로젝트에서 @Scheduled 쓸 거야"**라고 알려주는 스위치입니다.

```java
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
```
