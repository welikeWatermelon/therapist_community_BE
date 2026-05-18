# SSE 실시간 알림, 서버가 늘어나면 어떻게 될까? — Redis Pub/Sub과 Nginx 설정까지

## 들어가며

MelonMe 백엔드에는 SSE(Server-Sent Events) 기반 실시간 알림 시스템이 있습니다. 댓글이 달리거나, 반응이 달리거나, 치료사 인증이 승인되면 즉시 클라이언트에 이벤트를 밀어넣는 기능입니다.

단일 서버에서는 잘 돌아갑니다. 그런데 서버가 두 대가 되는 순간 이 시스템은 조용히 망가집니다.

---

## 현재 구조: 단일 서버에서의 SSE

MelonMe의 SSE 알림 흐름은 다음과 같습니다.

```
댓글 서비스
  → ApplicationEventPublisher.publishEvent(NotificationEvent)
    → @TransactionalEventListener(AFTER_COMMIT)
      → @Async 전용 스레드풀
        → DB 저장 + SSE 전송
```

SSE Emitter는 서버 메모리에 이렇게 저장됩니다.

```java
// 사용자 ID → (emitterId → SseEmitter)
ConcurrentHashMap<Long, ConcurrentHashMap<String, SseEmitter>> emitters;
```

`ConcurrentHashMap` 중첩 구조로 다중 탭(사용자당 N개의 Emitter)을 지원하고, `Last-Event-ID` 헤더로 재연결 시 유실 이벤트를 복구합니다.

이 설계는 **단일 서버에서는 완벽하게 동작합니다.**

---

## 문제: 서버가 2대가 되면

운영 트래픽이 늘어나 서버를 수평 확장(Scale-Out)하는 순간 문제가 생깁니다.

```
사용자 A의 브라우저
  └─ SSE 연결 ──▶ 서버 1 (emitters에 A의 Emitter 저장)

사용자 B가 A의 글에 댓글 작성
  └─ POST 요청 ──▶ 서버 2 (로드밸런서가 서버 2로 라우팅)
        └─ publishEvent(NotificationEvent for A)
              └─ 서버 2의 emitters에서 A를 찾는다
                    → 없음. 서버 1에 있으니까.
                          → 알림 유실 💥
```

서버 2는 서버 1의 메모리를 볼 수 없습니다. `ConcurrentHashMap`은 프로세스 내부에만 존재하기 때문입니다. 이것이 **단일 JVM 한계**입니다.

---

## 해결책: Redis Pub/Sub 도입

이 문제의 업계 표준 해결책은 Redis의 Pub/Sub 기능을 이벤트 브로커로 사용하는 것입니다.

```
서버 2에서 이벤트 발생
  → Redis 채널("notification:userId:A")에 publish

서버 1은 해당 채널을 subscribe 중
  → 메시지 수신
  → 서버 1 메모리의 A Emitter에 전송
  → A의 브라우저에 알림 도달 ✅

서버 3, 서버 4도 같은 채널을 subscribe
  → 각자 자기 서버에 연결된 A의 Emitter를 찾아 전송 시도
  → A의 Emitter가 없으면 아무것도 하지 않음 (무해)
```

### 구조 변화

**현재 (단일 서버)**
```
이벤트 발생 → ConcurrentHashMap(로컬 메모리) → SseEmitter.send()
```

**개선 후 (다중 서버)**
```
이벤트 발생 → Redis publish → [모든 서버가 수신] → 로컬 Emitter 탐색 → SseEmitter.send()
```

### 구현 설계 (Spring MVC 기반)

MelonMe는 Spring WebFlux가 아닌 Spring MVC 기반이므로, `RedisMessageListenerContainer`를 사용하는 방식으로 구현합니다.

**1. Redis 채널 발행 (이벤트 발생 서버)**

```java
// NotificationService에서 이벤트 발생 시
String channel = "notification:" + targetUserId;
redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(notificationEvent));
```

**2. Redis 채널 구독 (모든 서버)**

```java
@Component
public class NotificationRedisSubscriber implements MessageListener {

    private final SseEmitterRepository emitterRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        NotificationEvent event = objectMapper.readValue(message.getBody(), NotificationEvent.class);
        // 현재 서버에 해당 사용자의 Emitter가 있으면 전송
        emitterRepository.findByUserId(event.getTargetUserId())
            .forEach(emitter -> sendToEmitter(emitter, event));
    }
}

@Bean
public RedisMessageListenerContainer redisMessageListenerContainer(
        NotificationRedisSubscriber subscriber) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(redisConnectionFactory);
    // 전체 notification 채널 패턴 구독
    container.addMessageListener(subscriber, new PatternTopic("notification:*"));
    return container;
}
```

**3. 채널 채택 전략**

| 채널 전략 | 예시 | 특징 |
|-----------|------|------|
| 사용자별 채널 | `notification:userId:123` | 불필요한 메시지 최소화 |
| 전체 채널 | `notifications` | 단순하지만 모든 서버가 모든 이벤트 수신 |
| 서버별 채널 | `notification:server:A` | 복잡하지만 정밀한 라우팅 가능 |

MelonMe 규모에서는 **사용자별 채널** 전략이 가장 적합합니다.

### 트레이드오프

이 구조에서 Redis가 다운되면 알림이 전달되지 않습니다. 단, 알림은 이미 DB에 저장되어 있으므로 **이벤트 유실은 없고 실시간 전달만 중단**됩니다. 클라이언트가 재연결하거나 알림 목록을 조회하면 확인할 수 있습니다. 커뮤니티 알림의 특성상 이 트레이드오프는 합리적입니다.

---

## 추가: Nginx 뒤에 SSE를 두면 생기는 문제

Scale-Out 이전에도, 단일 서버 운영 중에 프록시 서버(Nginx) 뒤에 SSE를 배포하면 조용히 연결이 끊기는 문제가 있습니다.

### 왜 끊길까

Nginx는 기본적으로 업스트림 서버(Spring Boot)의 응답을 버퍼에 모았다가 클라이언트에게 보냅니다. SSE는 응답이 끝나지 않는 스트리밍 프로토콜인데, Nginx가 이걸 일반 HTTP 응답처럼 취급하면 두 가지 문제가 생깁니다.

1. **버퍼링** — Nginx가 응답을 버퍼에 쌓고 있어서 이벤트가 클라이언트에게 즉시 도달하지 않음. 심하면 수십 초 지연.
2. **타임아웃** — Nginx의 기본 `proxy_read_timeout`은 60초입니다. 이 시간 동안 데이터가 오지 않으면 Nginx가 연결을 강제로 끊습니다. 사용자가 알림을 기다리며 조용히 있으면 60초마다 SSE가 끊기고, EventSource가 재연결을 반복합니다.

### MelonMe의 현재 SSE 설정

```java
// SseEmitter 타임아웃: 30분
SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
```

Spring Boot 레벨에서는 30분 타임아웃으로 설정되어 있지만, Nginx가 60초에 연결을 끊어버리면 이 설정이 무의미합니다.

### Nginx 설정 수정

SSE 엔드포인트(`/api/v1/notifications/subscribe`)에 대해 다음 설정이 필요합니다.

```nginx
location /api/v1/notifications/subscribe {
    proxy_pass http://spring_backend;

    # SSE는 청크 스트리밍 — 버퍼링 OFF
    proxy_buffering off;
    proxy_cache off;

    # 타임아웃을 SSE emitter 타임아웃(30분)보다 길게
    proxy_read_timeout 35m;
    proxy_send_timeout 35m;

    # HTTP/1.1 필수 (keep-alive 유지)
    proxy_http_version 1.1;
    proxy_set_header Connection "";

    # SSE에 필요한 헤더
    proxy_set_header Cache-Control no-cache;
    proxy_set_header X-Accel-Buffering no;
}
```

**설정 포인트별 이유:**

| 설정 | 이유 |
|------|------|
| `proxy_buffering off` | Nginx가 응답을 버퍼에 모으지 않고 즉시 클라이언트에 전달 |
| `proxy_read_timeout 35m` | Spring Boot의 SseEmitter 타임아웃(30분)보다 5분 여유 |
| `proxy_http_version 1.1` | HTTP/1.0은 Connection: keep-alive를 지원하지 않음 |
| `proxy_set_header Connection ""` | 업스트림 연결을 keep-alive로 유지 (기본값 close 방지) |
| `X-Accel-Buffering no` | Nginx 버퍼링을 헤더 레벨에서도 비활성화 (더블 안전장치) |

### 확인 방법

배포 후 브라우저 DevTools → Network 탭에서 `/subscribe` 요청을 보면, 이벤트가 60초 이내에 끊기지 않고 30분간 유지되는지 확인할 수 있습니다.

---

## 정리

| 문제 | 발생 시점 | 해결 방법 |
|------|-----------|-----------|
| 다중 서버 알림 유실 | Scale-Out 이후 | Redis Pub/Sub으로 이벤트 브로커 도입 |
| Nginx 버퍼링으로 이벤트 지연 | Nginx 배포 직후 | `proxy_buffering off` |
| Nginx 60초 타임아웃으로 SSE 강제 종료 | Nginx 배포 직후 | `proxy_read_timeout 35m` |

MelonMe는 현재 단일 서버 환경에서 Last-Event-ID 기반 유실 복구까지 갖춘 SSE 시스템을 운영하고 있습니다. 서비스가 성장해 Scale-Out이 필요한 시점에 Redis Pub/Sub을 도입하고, Nginx 설정을 챙기면 현재 구조를 크게 바꾸지 않고도 대응할 수 있습니다.

---

## 참고

- [InfoQ: Reactive Real-Time Notifications with SSE, Spring Boot, and Redis Pub/Sub](https://www.infoq.com/articles/reactive-notification-system-server-sent-events/)
- [DEV.to: Server Sent Events are still not production ready](https://dev.to/miketalbot/server-sent-events-are-still-not-production-ready-after-a-decade-a-lesson-for-me-a-warning-for-you-2gie)
