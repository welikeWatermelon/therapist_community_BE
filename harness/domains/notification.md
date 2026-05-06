# Notification Domain

- Path: `src/main/java/com/therapyCommunity_Vol1/backend/notification`

## 책임

- 알림 저장, unread count, read 처리, SSE 구독, missed event replay를 맡는다.

## 진입점

- `NotificationController`
- `NotificationService`
- `NotificationEventListener`
- `SseEmitterRepository`

## 주요 모델

- `Notification`
- `NotificationType`

## 연동

- 다른 도메인의 event payload를 받아 AFTER_COMMIT 후처리한다.
- emitter와 event cache는 현재 메모리 기반이다.
- 상세 배경은 [docs/architecture/NOTIFICATION.md](../../docs/architecture/NOTIFICATION.md) 참고.

## 변경 체크

- sender 자신에게 알림이 가지 않도록 receiver filtering을 유지한다.
- 다중 인스턴스로 가기 전까지는 메모리 emitter 한계를 깨닫고 설계해야 한다.
- SSE 연결 실패가 원본 비즈니스 로직을 롤백시키지 않게 본다.
