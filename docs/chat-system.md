# 채팅(쪽지) 시스템

## 1. 한눈에 보기

- 치료사 커뮤니티 회원 간 1:1 쪽지를 주고받을 수 있다.
- 메시지를 보내면 DB에 저장되고, 기존 알림 시스템(SSE)을 통해 상대방에게 실시간으로 알림이 간다.
- 두 유저 사이에 대화방은 하나만 존재하며, 탈퇴한 유저에게는 메시지를 보낼 수 없다.

---

## 2. 전체 흐름 (간단)

```
[클라이언트 A]                [서버]                           [클라이언트 B]
     │                          │                                  │
  메시지 전송                   │                                  │
  POST /conversations           │                                  │
  /{id}/messages                │                                  │
     │                          │                                  │
     ├──────────────────►  ChatService                             │
     │                     ├─ Message DB 저장                      │
     │                     ├─ Conversation.lastMessage 갱신        │
     │                     └─ publishEvent(NEW_MESSAGE)            │
     │                          │                                  │
     │                    ── 트랜잭션 COMMIT ──                    │
     │                          │                                  │
     │                  NotificationEventListener                  │
     │                     ├─ Notification DB 저장                 │
     │                     └─ SSE 전송 ───────────────────────► 실시간 수신
     │                          │                                  │
     ◄──── 201 Created ────────┘                                  │
```

---

## 3. 전체 흐름 (상세)

### 3.1 클래스별 역할과 책임

| 클래스 | 패키지 | 역할 |
|--------|--------|------|
| `Conversation` | `domain/` | 대화방 엔티티. 두 참여자(participant1/2) + 마지막 메시지 미리보기 |
| `Message` | `domain/` | 메시지 엔티티. 대화방 소속, 발신자, 내용, 읽음 여부 |
| `ChatService` | `service/` | 핵심 비즈니스 로직. 대화 생성, 메시지 전송/조회, 읽음 처리, 접근 제어 |
| `ChatController` | `controller/` | REST API 6개 엔드포인트 |
| `ConversationRepository` | `repository/` | 대화방 조회 (참여자 검색, 페이징, 안읽은 수) |
| `MessageRepository` | `repository/` | 메시지 조회 (cursor 페이징) + bulk 읽음 처리 + unread 배치 쿼리 |
| `NotificationType.NEW_MESSAGE` | `notification/domain/` | 알림 유형. `"%s님이 메시지를 보냈습니다."` |

### 3.2 클래스 간 호출 관계

```
ChatController
    │
    ▼
ChatService
    │
    ├─ ConversationRepository     ← 대화방 CRUD
    ├─ MessageRepository          ← 메시지 CRUD + bulk update
    ├─ UserRepository             ← 유저 조회 (sender, recipient)
    └─ ApplicationEventPublisher  ← NotificationEvent 발행
                │
                ▼  (트랜잭션 COMMIT 후)
        NotificationEventListener (@Async)
                │
                ▼
        NotificationService.createAndSend()
                │
                ├─ Notification DB 저장
                └─ SSE 실시간 전송
```

### 3.3 participant 정렬 로직

두 유저 사이에 대화방이 하나만 존재하도록 **UNIQUE(participant1_id, participant2_id)** 제약을 사용한다.
(A→B)와 (B→A) 요청이 같은 행을 가리키도록, **항상 작은 ID를 participant1에 배치**한다.

```
Conversation.create(userA, userB):
    if (userA.id < userB.id):
        participant1 = userA
        participant2 = userB
    else:
        participant1 = userB
        participant2 = userA

ChatService.createConversation():
    smallerId = Math.min(senderId, recipientId)
    largerId  = Math.max(senderId, recipientId)
    → findByParticipants(smallerId, largerId)
```

DB 레벨에서도 `CHECK(participant1_id < participant2_id)` 제약으로 이를 강제한다.

### 3.4 cursor 기반 페이징 동작 방식

메시지 목록은 **offset 페이징이 아닌 cursor 방식**을 사용한다. 새 메시지가 계속 추가되는 채팅 특성상 offset은 페이지 밀림(중복/누락)이 발생하기 때문이다.

```
첫 로드 (before=null):
    SELECT * FROM messages
    WHERE conversation_id = :id
    ORDER BY id DESC
    LIMIT :size + 1          ← 1개 더 가져와서 hasNext 판단

이전 메시지 로드 (before=30):
    SELECT * FROM messages
    WHERE conversation_id = :id AND id < 30
    ORDER BY id DESC
    LIMIT :size + 1

결과 처리:
    messages = [30, 20, 10, 5]    ← DB에서 ID 역순으로 가져옴
    hasNext = (4개 > size 3)? → true
    page = [30, 20, 10]           ← size개만 자르기
    reverse → [10, 20, 30]        ← 시간순으로 뒤집어서 반환
    nextCursor = 10               ← reverse 후 첫 번째 = 가장 오래된 ID
```

클라이언트 사용 예:
```
1. GET /conversations/1/messages?size=50          → 최신 50개
2. GET /conversations/1/messages?before=10&size=50 → ID 10 이전 50개
3. hasNext=false가 올 때까지 반복
```

### 3.5 알림 시스템 연동 (SSE 재활용)

채팅 알림은 기존 알림 시스템을 **그대로 재활용**한다. 별도 SSE 채널이나 WebSocket 없이, `NotificationType`에 `NEW_MESSAGE`를 추가하고 `ApplicationEventPublisher`로 이벤트를 발행하면 된다.

```java
// ChatService — 메시지 전송 후
eventPublisher.publishEvent(NotificationEvent.of(
    senderId, recipientId,
    NotificationType.NEW_MESSAGE, conversationId));  // referenceId = conversationId
```

**동작 흐름:**
1. `ChatService`가 `NotificationEvent` 발행
2. 트랜잭션 COMMIT 후, `NotificationEventListener`가 `@Async` 별도 스레드에서 처리
3. `NotificationService.createAndSend()`가 Notification DB 저장 + SSE 전송
4. `NotificationService`가 자동으로 `senderId == receiverId`를 필터링하므로 자기 알림은 발생하지 않음
5. 클라이언트는 SSE 이벤트의 `type: NEW_MESSAGE`, `referenceId: {conversationId}`를 보고 해당 대화방으로 이동

---

## 4. API 엔드포인트 정리

모든 API는 `Authorization: Bearer {token}` 헤더 필수. 베이스 경로: `/api/v1/conversations`

### POST / — 대화 시작

상대방과의 대화를 시작하고 첫 메시지를 전송한다. 기존 대화가 있으면 메시지만 추가한다.

**요청:**
```json
{
    "recipientId": 2,
    "content": "안녕하세요"
}
```

| 필드 | 검증 |
|------|------|
| `recipientId` | `@NotNull` |
| `content` | `@NotBlank`, `@Size(max=1000)` |

**응답:** 새 대화 `201 Created`, 기존 대화 `200 OK`
```json
{
    "success": true,
    "data": {
        "id": 1,
        "otherUserId": 2,
        "otherUserNickname": "김치료사",
        "otherUserProfileImageUrl": "profile.jpg",
        "lastMessageContent": "안녕하세요",
        "lastMessageAt": "2026-04-28T10:00:00",
        "unreadCount": 0,
        "createdAt": "2026-04-28T10:00:00"
    }
}
```

### GET / — 내 대화 목록

참여 중인 대화 목록을 최신 메시지 순으로 조회한다. offset 페이징.

**파라미터:** `page` (기본 0), `size` (기본 20, 최대 50)

**응답:**
```json
{
    "success": true,
    "data": {
        "items": [
            {
                "id": 1,
                "otherUserId": 2,
                "otherUserNickname": "김치료사",
                "otherUserProfileImageUrl": "profile.jpg",
                "lastMessageContent": "네, 감사합니다",
                "lastMessageAt": "2026-04-28T11:00:00",
                "unreadCount": 3,
                "createdAt": "2026-04-28T10:00:00"
            }
        ],
        "page": 0,
        "size": 20,
        "totalElements": 5,
        "totalPages": 1,
        "hasNext": false
    }
}
```

**N+1 방지:** 대화 목록 조회(fetch join) 1회 + unreadCount 배치 쿼리 1회 = **총 2회 쿼리**

### GET /{conversationId}/messages — 메시지 목록

특정 대화의 메시지를 **cursor 기반**으로 조회한다.

**파라미터:** `before` (메시지 ID, 생략 시 최신부터), `size` (기본 50, 최대 50)

**응답:**
```json
{
    "success": true,
    "data": {
        "items": [
            {
                "id": 10,
                "conversationId": 1,
                "senderId": 1,
                "senderNickname": "박상담사",
                "content": "안녕하세요",
                "read": true,
                "createdAt": "2026-04-28T10:00:00"
            },
            {
                "id": 20,
                "conversationId": 1,
                "senderId": 2,
                "senderNickname": "김치료사",
                "content": "네, 반갑습니다",
                "read": false,
                "createdAt": "2026-04-28T10:01:00"
            }
        ],
        "size": 50,
        "hasNext": true,
        "nextCursor": 10
    }
}
```

| 필드 | 설명 |
|------|------|
| `items` | 시간순(오래된→최신) 정렬 |
| `hasNext` | 이전 메시지가 더 있는지 |
| `nextCursor` | 다음 요청 시 `before` 파라미터에 넘길 값 |

### POST /{conversationId}/messages — 메시지 전송

기존 대화에 메시지를 전송한다.

**요청:**
```json
{
    "content": "추가 메시지입니다"
}
```

| 필드 | 검증 |
|------|------|
| `content` | `@NotBlank`, `@Size(max=1000)` |

**응답:** `201 Created`
```json
{
    "success": true,
    "data": {
        "id": 30,
        "conversationId": 1,
        "senderId": 1,
        "senderNickname": "박상담사",
        "content": "추가 메시지입니다",
        "read": false,
        "createdAt": "2026-04-28T11:00:00"
    }
}
```

### PATCH /{conversationId}/read — 읽음 처리

대화 내 상대방 메시지를 모두 읽음 처리한다. bulk UPDATE로 처리.

**응답:** `204 No Content`

### GET /unread-count — 안읽은 대화 수

읽지 않은 메시지가 있는 대화 수를 반환한다.

**응답:**
```json
{
    "success": true,
    "data": {
        "unreadCount": 3
    }
}
```

---

## 5. 도메인 규칙

### 자기 자신에게 메시지 불가

```java
if (senderId.equals(request.getRecipientId())) {
    throw new CustomException(ErrorCode.CANNOT_MESSAGE_SELF);
}
```

대화 시작(`createConversation`) 시점에 체크. `sendMessage`는 이미 대화방이 존재하므로 구조적으로 불가.

### 탈퇴 유저에게 메시지 불가

```java
if (recipient.isWithdrawn()) {
    throw new CustomException(ErrorCode.CANNOT_MESSAGE_WITHDRAWN_USER);
}
```

- **대화 생성 시:** recipient의 `isWithdrawn()` 체크
- **기존 대화에서 전송 시:** `conversation.getOtherParticipant(senderId).isWithdrawn()` 체크
- **기존 대화 내역:** 그대로 유지. 탈퇴 유저는 `getDisplayNickname()`으로 **"탈퇴한 회원"** 표시

### 참여자만 대화 접근 가능

모든 대화 관련 엔드포인트에서 `validateParticipant()` 호출:

| 엔드포인트 | 접근 제어 방식 |
|-----------|---------------|
| GET /conversations | 쿼리 자체가 userId 기반 필터 |
| GET /conversations/{id}/messages | `validateParticipant()` |
| POST /conversations/{id}/messages | `validateParticipant()` |
| PATCH /conversations/{id}/read | `validateParticipant()` |
| GET /conversations/unread-count | 쿼리 자체가 userId 기반 필터 |

### 대화방은 두 유저 사이 하나만 존재

- DB: `UNIQUE(participant1_id, participant2_id)` + `CHECK(participant1_id < participant2_id)`
- 엔티티: `Conversation.create()`에서 ID 크기순 정렬
- 서비스: `findByParticipants(smallerId, largerId)` → 있으면 기존 반환, 없으면 생성
- 동시 요청: `DataIntegrityViolationException` catch 후 재조회

---

## 6. 에러 처리 및 보안

### ErrorCode 목록

| ErrorCode | HTTP | 코드 | 메시지 | 발생 조건 |
|-----------|------|------|--------|----------|
| `CONVERSATION_NOT_FOUND` | 404 | `CHAT_404` | 대화를 찾을 수 없습니다. | 존재하지 않는 대화 ID **또는** 비참여자 접근 |
| `CONVERSATION_ACCESS_DENIED` | 403 | `CHAT_403` | 대화에 대한 접근 권한이 없습니다. | (미사용 — 보안상 404로 통일) |
| `CANNOT_MESSAGE_SELF` | 400 | `CHAT_400_SELF` | 자기 자신에게 메시지를 보낼 수 없습니다. | recipientId == senderId |
| `CANNOT_MESSAGE_WITHDRAWN_USER` | 400 | `CHAT_400_WITHDRAWN` | 탈퇴한 회원에게 메시지를 보낼 수 없습니다. | 상대방 탈퇴 |

### 404 통일 이유

비참여자가 `GET /conversations/999/messages`를 요청할 때:

- **403 응답:** "대화 999가 존재하지만 권한이 없다" → 대화 존재 여부가 노출됨
- **404 응답:** "대화 999를 찾을 수 없다" → 존재하는지 알 수 없음

공격자가 conversationId를 brute-force하여 다른 유저 간의 대화 존재 여부를 확인하는 것을 방지하기 위해 **404로 통일**한다. 실제 존재하지 않는 conversationId도 동일하게 404를 반환하므로 구분이 불가능하다.

```java
private void validateParticipant(Conversation conversation, Long userId) {
    if (!conversation.isParticipant(userId)) {
        // 비참여자에게 대화 존재 여부를 노출하지 않기 위해 404로 통일
        throw new CustomException(ErrorCode.CONVERSATION_NOT_FOUND);
    }
}
```

---

## 7. 새 도메인 규칙 추가 시 체크리스트

채팅 시스템에 새 기능이나 규칙을 추가할 때 확인할 사항:

### 새 검증 로직 추가

- [ ] `createConversation`과 `sendMessage` **양쪽 모두**에 적용했는가? (대화 생성 시 / 기존 대화 전송 시)
- [ ] 검증 실패 시 적절한 `ErrorCode`를 추가했는가? (HTTP 상태코드 매핑)
- [ ] 비참여자에게 정보가 노출되지 않는가? (403 대신 404 사용)

### 새 필드 추가

- [ ] `Conversation` 또는 `Message`에 필드를 추가했으면 Flyway 마이그레이션을 작성했는가?
- [ ] Response DTO의 `from()` 팩토리 메서드를 업데이트했는가?
- [ ] 새 필드가 인덱스가 필요한 조회 조건인가?

### 새 조회 쿼리 추가

- [ ] `OR` 조건에 괄호를 올바르게 배치했는가? (AND 우선순위 주의)
- [ ] `LEFT JOIN FETCH` + `Page` 조합이면 `countQuery`를 분리했는가?
- [ ] N+1이 발생하지 않는가? (`@EntityGraph` 또는 배치 쿼리 사용)

### 테스트

- [ ] 정상 케이스 + 접근 제어 + 예외 케이스를 모두 커버했는가?
- [ ] 기존 테스트가 깨지지 않았는가? (`./gradlew test`)

---

## 8. 트러블슈팅 기록

### 이슈 1: JPQL OR/AND 우선순위 괄호 누락

**발생 조건:** `countUnreadConversations` 쿼리에서 안읽은 대화 수 조회 시

**원인:** JPQL에서 `OR`와 `AND`를 괄호 없이 혼용하면, `AND`가 먼저 평가된다.

```sql
-- 잘못된 쿼리: participant1이면 모든 메시지가 카운트됨
WHERE m.conversation.participant1.id = :userId
   OR m.conversation.participant2.id = :userId
  AND m.sender.id != :userId AND m.read = false

-- 실제 평가 순서 (AND가 먼저):
WHERE participant1 = :userId
   OR (participant2 = :userId AND sender != :userId AND read = false)
```

**해결:** OR 조건을 괄호로 감싸서 의도한 평가 순서를 명시

```sql
WHERE (m.conversation.participant1.id = :userId OR m.conversation.participant2.id = :userId)
  AND m.sender.id != :userId AND m.read = false
```

**파일:** `ConversationRepository.java`

---

### 이슈 2: 대화방 생성 race condition

**발생 조건:** 두 유저가 동시에 서로에게 첫 메시지를 보낼 때

**원인:** "조회 → 없으면 생성" 패턴에서 두 트랜잭션이 동시에 조회하면 둘 다 "없음"으로 판단하고 INSERT를 시도한다. `UNIQUE(participant1_id, participant2_id)` 제약 위반으로 하나가 실패하여 500 에러 반환.

```
TX1: findByParticipants(1,2) → empty
TX2: findByParticipants(1,2) → empty
TX1: INSERT → 성공
TX2: INSERT → DataIntegrityViolationException (UNIQUE 위반)
```

**해결:** `DataIntegrityViolationException`을 catch하여 재조회

```java
try {
    conversation = Conversation.create(sender, recipient, content);
    conversation = conversationRepository.saveAndFlush(conversation);
    created = true;
} catch (DataIntegrityViolationException e) {
    // 동시 생성된 대화를 재조회
    conversation = conversationRepository.findByParticipants(smallerId, largerId)
            .orElseThrow(...);
    created = false;
}
```

`save()` 대신 `saveAndFlush()`를 사용하여 UNIQUE 제약 위반이 catch 블록에서 즉시 잡히도록 한다.

**파일:** `ChatService.java`

---

### 이슈 3: fetch join + Pageable 메모리 페이징

**발생 조건:** 대화 목록 조회 시 `LEFT JOIN FETCH` + `Page` 조합 사용

**원인:** Spring Data JPA가 `@Query`에서 count 쿼리를 자동 생성할 때, FETCH JOIN이 포함된 채로 count 쿼리가 실행될 수 있다. 불필요한 JOIN이 count 쿼리 성능을 저하시키고, Hibernate가 경고(HHH90003004)를 발생시킬 수 있다.

**해결:** `countQuery`를 명시적으로 분리

```java
@Query(value = "SELECT c FROM Conversation c " +
        "LEFT JOIN FETCH c.participant1 " +
        "LEFT JOIN FETCH c.participant2 " +
        "WHERE c.participant1.id = :userId OR c.participant2.id = :userId " +
        "ORDER BY c.lastMessageAt DESC",
        countQuery = "SELECT COUNT(c) FROM Conversation c " +
        "WHERE c.participant1.id = :userId OR c.participant2.id = :userId")
Page<Conversation> findByParticipantId(...);
```

**파일:** `ConversationRepository.java`

---

### 이슈 4: unreadCount N+1

**발생 조건:** 대화 목록 조회 시 각 대화별 안읽은 메시지 수 조회

**원인:** 대화 N개에 대해 개별 COUNT 쿼리를 실행하면 N+1 문제 발생.

**해결:** 대화 ID 목록을 한 번에 넘겨 배치 쿼리로 처리

```java
// MessageRepository — 1회 쿼리로 전체 unreadCount 조회
@Query("SELECT m.conversation.id, COUNT(m) FROM Message m " +
        "WHERE m.conversation.id IN :conversationIds " +
        "AND m.sender.id != :userId AND m.read = false " +
        "GROUP BY m.conversation.id")
List<Object[]> countUnreadByConversationIds(...);

// ChatService — Map으로 변환하여 O(1) 조회
Map<Long, Long> unreadCounts = getUnreadCountMap(conversationIds, userId);
responses = conversations.stream()
    .map(c -> ConversationResponse.from(c, userId,
            unreadCounts.getOrDefault(c.getId(), 0L)))
    .toList();
```

**쿼리 수:** 대화 목록 1회 + unreadCount 배치 1회 = **총 2회** (N과 무관)

**파일:** `MessageRepository.java`, `ChatService.java`

---

### 이슈 5: offset 페이징 → cursor 페이징 전환

**발생 조건:** 메시지 목록 조회 중 새 메시지가 도착할 때

**원인:** offset 방식(`page=0&size=20`)에서 새 메시지가 추가되면 기존 메시지들의 offset이 밀린다.

```
상태: [A, B, C, D, E]  (size=3)
page=0 → [A, B, C]     ← 정상
(새 메시지 F 도착)
상태: [A, B, C, D, E, F]
page=1 → [D, E, F]     ← C가 page=0에도, page=1에도 없음 (누락)
```

**해결:** cursor 방식으로 전환. `before={messageId}`를 기준으로 조회하므로 새 메시지에 영향 없음.

```
상태: [A(1), B(2), C(3), D(4), E(5)]  (size=3)
첫 로드 → [C, D, E] (최신 3개), nextCursor=3
(새 메시지 F(6) 도착)
before=3 → [A, B] ← 정확히 이전 메시지만
```

**파일:** `MessageRepository.java`, `ChatService.java`, `ChatController.java`, `CursorPageResponse.java`

---

### 이슈 6: 403 → 404 보안 통일

**발생 조건:** 비참여자가 다른 사람의 대화방에 접근할 때

**원인:** `403 Forbidden`은 "리소스가 존재하지만 권한이 없다"는 의미. 공격자가 conversationId를 순회하며 403/404 차이로 대화 존재 여부를 확인할 수 있다.

```
GET /conversations/999/messages → 403  → "대화 999는 존재한다"
GET /conversations/998/messages → 404  → "대화 998은 없다"
```

**해결:** `validateParticipant()`에서 `CONVERSATION_NOT_FOUND(404)`를 던져 존재하지 않는 경우와 동일한 응답을 반환

**파일:** `ChatService.java`

---

### 이슈 7: lastMessageContent 동시성 (미해결 TODO)

**발생 조건:** 두 참여자가 동시에 메시지를 전송할 때

**원인:** `conversation.updateLastMessage()`가 `LocalDateTime.now()`로 `lastMessageAt`을 갱신하는데, 두 트랜잭션이 거의 동시에 커밋되면 나중에 커밋된 트랜잭션이 이긴다 (last-writer-wins). 메시지 목록은 정확하지만, 대화 목록의 미리보기(`lastMessageContent`)가 실제 마지막 메시지와 다를 수 있다.

```
TX1: message="A" → updateLastMessage("A", T1) → COMMIT
TX2: message="B" → updateLastMessage("B", T2) → COMMIT
결과: lastMessageContent="B" (나중에 커밋된 것)
실제 마지막 메시지: A일 수도 B일 수도 있음
```

**현재 상태:** TODO로 남김. 치료사 커뮤니티 규모에서 미리보기가 간혹 틀리는 것은 허용 가능. 메시지 목록 자체는 항상 정확하다.

**향후 개선안:** 트래픽 증가 시 `SELECT FOR UPDATE` 또는 `lastMessageAt` 비교 후 조건부 갱신으로 해결 가능.

**파일:** `ChatService.java`

---

## DB 스키마

```sql
-- V30__create_conversations_and_messages.sql

CREATE TABLE conversations (
    id                   BIGSERIAL    PRIMARY KEY,
    participant1_id      BIGINT       NOT NULL REFERENCES users(id),
    participant2_id      BIGINT       NOT NULL REFERENCES users(id),
    last_message_content VARCHAR(1000),
    last_message_at      TIMESTAMP    NOT NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE(participant1_id, participant2_id),
    CHECK(participant1_id < participant2_id)
);

CREATE TABLE messages (
    id              BIGSERIAL    PRIMARY KEY,
    conversation_id BIGINT       NOT NULL REFERENCES conversations(id),
    sender_id       BIGINT       NOT NULL REFERENCES users(id),
    content         VARCHAR(1000) NOT NULL,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 인덱스
CREATE INDEX idx_messages_conversation_created ON messages(conversation_id, created_at);
CREATE INDEX idx_conversations_participant1 ON conversations(participant1_id);
CREATE INDEX idx_conversations_participant2 ON conversations(participant2_id);
```

---

## 파일 구조

```
chat/
├── controller/
│   └── ChatController.java                ← REST API 6개 엔드포인트
├── domain/
│   ├── Conversation.java                  ← 대화방 엔티티 (participant 정렬, lastMessage)
│   └── Message.java                       ← 메시지 엔티티
├── dto/
│   ├── CreateConversationRequest.java     ← 대화 시작 요청 (recipientId + content)
│   ├── SendMessageRequest.java            ← 메시지 전송 요청 (content)
│   ├── ConversationResponse.java          ← 대화 응답 (상대방 정보 + unreadCount)
│   ├── MessageResponse.java               ← 메시지 응답
│   ├── CursorPageResponse.java            ← cursor 페이징 응답 (items, hasNext, nextCursor)
│   └── UnreadConversationCountResponse.java ← 안읽은 대화 수 응답
├── repository/
│   ├── ConversationRepository.java        ← 대화방 조회 + 안읽은 대화 수
│   └── MessageRepository.java            ← cursor 쿼리 + bulk 읽음 처리 + unread 배치
└── service/
    └── ChatService.java                   ← 비즈니스 로직 + 접근 제어 + 알림 발행
```
