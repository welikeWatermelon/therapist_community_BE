# 알림 시스템 사용 가이드

## 개요

백엔드에서 알림을 생성하고, 프론트엔드에서 SSE로 실시간 알림을 수신하는 방법을 설명합니다.

---

## 백엔드 사용법

### 1. NotificationService 주입

```java
@Service
@RequiredArgsConstructor
public class YourService {

    private final NotificationService notificationService;

    // ...
}
```

---

### 2. 알림 생성 메서드

| 메서드 | 용도 | 파라미터 |
|--------|------|----------|
| `createCommentNotification()` | 댓글 알림 | recipient, actor, postId, commentId |
| `createReplyNotification()` | 대댓글 알림 | recipient, actor, parentCommentId, replyId |
| `createPostReactionNotification()` | 게시글 반응 알림 | recipient, actor, postId, reactionType |
| `createCommentReactionNotification()` | 댓글 좋아요 알림 | recipient, actor, commentId |

---

### 3. 사용 예시

```java
@Service
@RequiredArgsConstructor
public class CommentService {

    private final NotificationService notificationService;
    private final PostRepository postRepository;

    @Transactional
    public void createComment(Long userId, Long postId, CreateCommentRequest request) {
        // 1. 댓글 저장 로직
        Comment comment = saveComment(userId, postId, request);

        // 2. 게시글 작성자에게 알림 전송
        Post post = postRepository.findById(postId).orElseThrow();
        User postAuthor = post.getAuthor();  // 알림 받을 사람
        User commenter = comment.getAuthor(); // 알림 보내는 사람

        notificationService.createCommentNotification(
            postAuthor,      // recipient (알림 받는 사람)
            commenter,       // actor (행동한 사람)
            postId,          // postId
            comment.getId()  // commentId
        );
    }
}
```

---

### 4. 새로운 알림 타입 추가하기

#### 4-1. NotificationType enum에 추가

```java
// notification/domain/NotificationType.java
public enum NotificationType {
    COMMENT,
    REPLY,
    POST_REACTION,
    COMMENT_REACTION,
    FOLLOW,          // 새로 추가
    MENTION          // 새로 추가
}
```

#### 4-2. NotificationService에 메서드 추가

```java
// notification/service/NotificationService.java

@Transactional
public void createFollowNotification(User recipient, User actor) {
    if (shouldSkipNotification(recipient, actor)) {
        return;
    }

    String message = String.format("%s님이 회원님을 팔로우했습니다.", actor.getNickname());
    createAndSaveNotification(
        recipient,
        actor,
        NotificationType.FOLLOW,
        actor.getId(),    // referenceId (팔로우한 사람 ID)
        "USER",           // referenceType
        message
    );
}
```

#### 4-3. 서비스에서 호출

```java
@Service
@RequiredArgsConstructor
public class FollowService {

    private final NotificationService notificationService;

    @Transactional
    public void follow(User follower, User target) {
        // 팔로우 로직...

        // 알림 전송
        notificationService.createFollowNotification(target, follower);
    }
}
```

---

### 5. 알림 흐름 요약

```
1. notificationService.createXxxNotification() 호출
    ↓
2. Notification 엔티티 DB 저장
    ↓
3. Redis 캐시 증가 (unreadCount +1)
    ↓
4. Outbox 이벤트 생성
    ↓
5. (트랜잭션 커밋)
    ↓
6. OutboxScheduler가 500ms 후 이벤트 감지
    ↓
7. SSE로 실시간 전송
```

---

### 6. 주의사항

```java
// ❌ 잘못된 사용 - 자기 자신에게 알림
notificationService.createCommentNotification(user, user, postId, commentId);
// → 내부에서 자동으로 스킵됨 (shouldSkipNotification)

// ✅ 올바른 사용 - 다른 사람에게 알림
notificationService.createCommentNotification(postAuthor, commenter, postId, commentId);
```

---

## 프론트엔드 사용법

### 1. SSE 연결 (event-source-polyfill 사용)

기본 `EventSource`는 Authorization 헤더를 지원하지 않으므로 라이브러리 사용을 권장합니다.

```bash
npm install event-source-polyfill
```

```javascript
import { EventSourcePolyfill } from "event-source-polyfill";

const token = "로그인 후 받은 accessToken";

const eventSource = new EventSourcePolyfill(
  "http://localhost:8080/api/v1/notifications/subscribe",
  {
    headers: {
      Authorization: `Bearer ${token}`
    }
  }
);

// 알림 수신
eventSource.addEventListener("notification", (event) => {
  const notification = JSON.parse(event.data);
  console.log("새 알림:", notification);
});

// 에러 처리
eventSource.onerror = (error) => {
  console.error("SSE 에러:", error);
  eventSource.close();
};

// 페이지 떠날 때 연결 해제
window.addEventListener("beforeunload", () => {
  eventSource.close();
});
```

---

### 2. React Hook 예시

```javascript
import { useEffect, useState } from "react";
import { EventSourcePolyfill } from "event-source-polyfill";

function useNotifications(token) {
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    if (!token) return;

    const eventSource = new EventSourcePolyfill(
      "/api/v1/notifications/subscribe",
      {
        headers: { Authorization: `Bearer ${token}` }
      }
    );

    eventSource.addEventListener("notification", (event) => {
      const newNotification = JSON.parse(event.data);
      setNotifications((prev) => [newNotification, ...prev]);
      setUnreadCount((prev) => prev + 1);
    });

    eventSource.onerror = () => {
      eventSource.close();
      // 재연결 로직
      setTimeout(() => {
        // 재연결 시도
      }, 5000);
    };

    return () => eventSource.close();
  }, [token]);

  return { notifications, unreadCount };
}
```

---

### 3. 알림 API 목록

| API | Method | 설명 |
|-----|--------|------|
| `/api/v1/notifications/subscribe` | GET | SSE 구독 |
| `/api/v1/notifications` | GET | 알림 목록 조회 (페이지네이션) |
| `/api/v1/notifications/unread-count` | GET | 안 읽은 개수 조회 |
| `/api/v1/notifications/{id}/read` | PATCH | 단일 읽음 처리 |
| `/api/v1/notifications/read-all` | PATCH | 전체 읽음 처리 |

---

### 4. 알림 데이터 형식

```json
{
  "id": 1,
  "actorId": 4,
  "actorNickname": "유저B",
  "actorProfileImageUrl": null,
  "notificationType": "COMMENT",
  "referenceId": 1,
  "referenceType": "COMMENT",
  "message": "유저B님이 회원님의 게시글에 댓글을 남겼습니다.",
  "createdAt": "2026-03-23T11:15:38",
  "read": false
}
```

### 5. 알림 타입 (notificationType)

| 타입 | 설명 |
|------|------|
| `COMMENT` | 내 게시글에 댓글 |
| `REPLY` | 내 댓글에 대댓글 |
| `POST_REACTION` | 내 게시글에 반응 |
| `COMMENT_REACTION` | 내 댓글에 좋아요 |
