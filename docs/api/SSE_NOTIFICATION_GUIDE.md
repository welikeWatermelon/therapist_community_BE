# SSE 실시간 알림 통합 가이드

## 목차
1. [개요](#개요)
2. [아키텍처](#아키텍처)
3. [엔드포인트](#엔드포인트)
4. [SSE 연결 구현](#sse-연결-구현)
5. [재연결 전략](#재연결-전략)
6. [알림 타입 및 라우팅](#알림-타입-및-라우팅)
7. [React 통합 예시](#react-통합-예시)
8. [에러 처리](#에러-처리)
9. [토큰 만료 처리](#토큰-만료-처리)
10. [주의사항 및 베스트 프랙티스](#주의사항-및-베스트-프랙티스)

---

## 개요

이 문서는 therapist_community 백엔드의 SSE(Server-Sent Events) 기반 실시간 알림 시스템을 프론트엔드에서 통합하는 방법을 설명합니다.

**주요 특징:**
- 실시간 알림 수신 (댓글, 반응, 스크랩, 인증 상태 등)
- 자동 재연결 지원 (`Last-Event-ID`로 유실 이벤트 복구)
- 30초 주기 하트비트 (연결 유지)
- 30분 타임아웃 후 자동 재연결
- 사용자당 최대 5개 동시 연결 지원
- 최대 50건 이벤트 캐시 (재연결 시 복구용)

---

## 아키텍처

```
┌─────────────────────────────────────────┐
│        프론트엔드 (React)                  │
│  ┌─────────────────────────────────────┐ │
│  │  NotificationProvider (Context)    │ │
│  │  - useNotification() hook           │ │
│  │  - 알림 상태 관리 + 라우팅          │ │
│  └─────────────────────────────────────┘ │
└────────────┬────────────────────────────┘
             │
      ┌──────▼──────┐
      │   SSE 연결   │ (EventSource 또는 fetch)
      └──────┬──────┘
             │ text/event-stream
             │
┌────────────▼────────────────────────────┐
│        백엔드 (Spring Boot)               │
│  ┌─────────────────────────────────────┐ │
│  │  NotificationController             │ │
│  │  /api/v1/notifications/subscribe    │ │
│  └─────────────────────────────────────┘ │
│                  │                        │
│  ┌──────────────▼──────────────────────┐ │
│  │  NotificationService                │ │
│  │  - SSE 연결 관리 (in-memory)        │ │
│  │  - 하트비트 (30초 간격)             │ │
│  │  - 이벤트 캐시 (50건 TTL 30분)      │ │
│  └──────────────┬──────────────────────┘ │
│                 │                         │
│  ┌──────────────▼──────────────────────┐ │
│  │  SseEmitterRepository                │ │
│  │  - 사용자별 Emitter 저장             │ │
│  │  - 이벤트 캐시 관리                  │ │
│  └─────────────────────────────────────┘ │
│                 │                         │
│  ┌──────────────▼──────────────────────┐ │
│  │  PostgreSQL Notifications Table      │ │
│  │  - 알림 영구 저장                    │ │
│  └─────────────────────────────────────┘ │
└──────────────────────────────────────────┘
```

**흐름:**
1. 사용자 액션 발생 (댓글, 반응, 스크랩 등)
2. 해당 Service가 `NotificationEvent` 발행
3. 비동기 리스너가 DB에 저장 + SSE로 실시간 전송
4. 클라이언트가 SSE 스트림으로 받음
5. Context/훅을 통해 UI 업데이트

---

## 엔드포인트

### 1. SSE 구독: GET `/api/v1/notifications/subscribe`

실시간 알림을 수신하기 위해 SSE 스트림에 연결합니다.

**인증:** Bearer 토큰 필수

**요청 헤더:**
```http
GET /api/v1/notifications/subscribe HTTP/1.1
Authorization: Bearer {accessToken}
Last-Event-ID: {lastEventId}  # 선택사항 — 유실 이벤트 복구
```

**응답:** `text/event-stream` (무한 스트림)

**응답 예시:**

연결 성공 이벤트:
```
event: connect
data: connected
```

알림 수신 이벤트:
```
id: 1_1699540816000
event: notification
data: {"id":1,"type":"NEW_COMMENT","content":"홍길동님이 회원님의 게시글에 댓글을 남겼습니다.","referenceId":42,"senderId":5,"senderNickname":"홍길동","read":false,"readAt":null,"createdAt":"2026-05-07T14:30:00"}
```

하트비트 (30초 주기):
```
: heartbeat
```

**에러:**
- `401 Unauthorized` — 토큰 없음 또는 만료
- `500 SSE_CONNECTION_ERROR` — 초기 연결 실패

---

### 2. 알림 목록 조회: GET `/api/v1/notifications`

저장된 알림을 페이징으로 조회합니다. (SSE 미수신 알림 확인 시 유용)

**인증:** Bearer 토큰 필수

**요청:**
```http
GET /api/v1/notifications?page=0&size=20 HTTP/1.1
Authorization: Bearer {accessToken}
```

**쿼리 파라미터:**
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `page` | int | 0 | 페이지 번호 (0-indexed) |
| `size` | int | 20 | 한 페이지 항목 수 |

**응답:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 1,
        "type": "NEW_COMMENT",
        "content": "홍길동님이 회원님의 게시글에 댓글을 남겼습니다.",
        "referenceId": 42,
        "senderId": 5,
        "senderNickname": "홍길동",
        "read": false,
        "readAt": null,
        "createdAt": "2026-05-07T14:30:00"
      }
    ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 42,
    "hasNext": true
  }
}
```

---

### 3. 미읽은 알림 수: GET `/api/v1/notifications/unread-count`

미읽은 알림 개수를 빠르게 조회합니다. (배지 표시용)

**인증:** Bearer 토큰 필수

**요청:**
```http
GET /api/v1/notifications/unread-count HTTP/1.1
Authorization: Bearer {accessToken}
```

**응답:**
```json
{
  "success": true,
  "data": {
    "count": 3
  }
}
```

---

### 4. 단건 읽음 처리: PATCH `/api/v1/notifications/{notificationId}/read`

특정 알림을 읽음 처리합니다.

**인증:** Bearer 토큰 필수

**요청:**
```http
PATCH /api/v1/notifications/1/read HTTP/1.1
Authorization: Bearer {accessToken}
```

**응답:**
```
204 No Content
```

**에러:**
- `404 NOTIFICATION_NOT_FOUND` — 알림 없음 또는 권한 없음

---

### 5. 전체 읽음 처리: PATCH `/api/v1/notifications/read-all`

모든 미읽은 알림을 읽음 처리합니다.

**인증:** Bearer 토큰 필수

**요청:**
```http
PATCH /api/v1/notifications/read-all HTTP/1.1
Authorization: Bearer {accessToken}
```

**응답:**
```
204 No Content
```

---

### 6. 알림 삭제: DELETE `/api/v1/notifications/{notificationId}`

특정 알림을 삭제합니다.

**인증:** Bearer 토큰 필수

**요청:**
```http
DELETE /api/v1/notifications/1 HTTP/1.1
Authorization: Bearer {accessToken}
```

**응답:**
```
204 No Content
```

**에러:**
- `404 NOTIFICATION_NOT_FOUND` — 알림 없음 또는 권한 없음

---

## SSE 연결 구현

### 방법 1: EventSource API (권장)

브라우저 내장 `EventSource`를 사용하면 가장 간단합니다. 단, 커스텀 헤더(Authorization) 지원이 제한적입니다.

**장점:**
- 브라우저 자동 재연결
- 간단한 코드

**단점:**
- 커스텀 헤더 지원 제한
- 토큰을 쿼리 파라미터로 전달해야 함 (보안 주의)

**예시:**
```typescript
const eventSource = new EventSource(
  `/api/v1/notifications/subscribe?token=${accessToken}`
);

eventSource.addEventListener('connect', (event) => {
  console.log('SSE 연결됨:', event.data);
});

eventSource.addEventListener('notification', (event) => {
  const notification = JSON.parse(event.data);
  console.log('알림 수신:', notification);
  // UI 업데이트
});

eventSource.onerror = (error) => {
  console.error('SSE 에러:', error);
  eventSource.close();
  // 재연결 로직
};
```

**주의:** 쿼리 파라미터 방식은 URL에 토큰이 노출되므로 HTTPS 사용 필수입니다.

---

### 방법 2: fetch + ReadableStream (권장)

`fetch`와 `ReadableStream`을 사용하면 커스텀 헤더를 완전히 제어할 수 있습니다. 토큰을 Authorization 헤더로 안전하게 전달할 수 있습니다.

**장점:**
- 커스텀 헤더 완전 지원
- 토큰을 Authorization 헤더로 전달 (보안)
- 더 많은 제어 가능

**단점:**
- 수동 재연결 필요
- 약간 더 복잡한 코드

**예시:**
```typescript
async function connectToNotificationStream(
  accessToken: string,
  lastEventId?: string
) {
  const headers: Record<string, string> = {
    'Authorization': `Bearer ${accessToken}`,
  };
  
  if (lastEventId) {
    headers['Last-Event-ID'] = lastEventId;
  }

  const response = await fetch('/api/v1/notifications/subscribe', {
    method: 'GET',
    headers,
  });

  if (!response.ok) {
    throw new Error(`SSE 연결 실패: ${response.status}`);
  }

  const reader = response.body?.getReader();
  if (!reader) throw new Error('ReadableStream 지원 안 함');

  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');

      // 마지막 불완전한 라인은 다음 반복에서 처리
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.trim() === '') continue;

        // SSE 형식 파싱
        if (line.startsWith('id: ')) {
          // eventId 파싱 (재연결 시 사용)
        } else if (line.startsWith('event: ')) {
          // eventName 파싱
        } else if (line.startsWith('data: ')) {
          // data 파싱
        }
      }
    }
  } finally {
    reader.releaseLock();
  }
}
```

**더 나은 구현 (이벤트 디스패치):**
```typescript
type SseEvent = {
  id?: string;
  event: string;
  data: string;
};

async function connectToNotificationStream(
  accessToken: string,
  lastEventId?: string,
  onEvent?: (event: SseEvent) => void
) {
  const headers: Record<string, string> = {
    'Authorization': `Bearer ${accessToken}`,
  };
  
  if (lastEventId) {
    headers['Last-Event-ID'] = lastEventId;
  }

  const response = await fetch('/api/v1/notifications/subscribe', {
    method: 'GET',
    headers,
  });

  if (!response.ok) {
    throw new Error(`SSE 연결 실패: ${response.status}`);
  }

  const reader = response.body?.getReader();
  if (!reader) throw new Error('ReadableStream 미지원');

  const decoder = new TextDecoder();
  let buffer = '';
  let currentEvent: Partial<SseEvent> = {};

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        // 빈 라인 = 이벤트 끝
        if (line.trim() === '') {
          if (currentEvent.event && currentEvent.data) {
            onEvent?.(currentEvent as SseEvent);
          }
          currentEvent = {};
          continue;
        }

        // 주석 (하트비트)
        if (line.startsWith(':')) {
          continue;
        }

        const colonIndex = line.indexOf(':');
        if (colonIndex === -1) continue;

        const key = line.substring(0, colonIndex);
        const value = line.substring(colonIndex + 2); // ': ' 제거

        if (key === 'id') {
          currentEvent.id = value;
        } else if (key === 'event') {
          currentEvent.event = value;
        } else if (key === 'data') {
          currentEvent.data = value;
        }
      }
    }
  } finally {
    reader.releaseLock();
  }
}
```

---

## 재연결 전략

SSE 연결은 언제든 끊길 수 있습니다. 효과적인 재연결 전략이 필요합니다.

### 지수 백오프 (Exponential Backoff)

연결 실패 시 대기 시간을 점진적으로 증가시킵니다.

**구현:**
```typescript
interface ReconnectConfig {
  initialDelayMs: number;    // 초기 대기 시간 (기본: 1000ms)
  maxDelayMs: number;        // 최대 대기 시간 (기본: 30000ms)
  backoffMultiplier: number; // 증가 배수 (기본: 2)
  maxRetries?: number;       // 최대 재시도 횟수 (선택)
}

class NotificationClient {
  private reconnectDelay: number;
  private lastEventId?: string;
  private isConnected = false;

  constructor(private config: ReconnectConfig) {
    this.reconnectDelay = config.initialDelayMs;
  }

  async connect(accessToken: string): Promise<void> {
    let retries = 0;

    while (true) {
      try {
        this.isConnected = true;
        this.reconnectDelay = this.config.initialDelayMs; // 성공 시 리셋

        await connectToNotificationStream(
          accessToken,
          this.lastEventId,
          (event) => this.handleEvent(event)
        );
      } catch (error) {
        this.isConnected = false;

        if (this.config.maxRetries && retries >= this.config.maxRetries) {
          console.error('최대 재시도 횟수 초과:', error);
          break;
        }

        console.warn(`재연결 시도 (${retries + 1}회):`, error);
        await this.delay(this.reconnectDelay);
        this.reconnectDelay = Math.min(
          this.reconnectDelay * this.config.backoffMultiplier,
          this.config.maxDelayMs
        );
        retries++;
      }
    }
  }

  private handleEvent(event: SseEvent): void {
    if (event.id) {
      this.lastEventId = event.id;
    }

    if (event.event === 'notification') {
      const notification = JSON.parse(event.data);
      // 알림 처리
    }
  }

  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}
```

### Last-Event-ID로 유실 이벤트 복구

연결이 끊겼을 때 마지막으로 받은 이벤트 ID를 저장하고, 재연결 시 전달하면 그 이후의 이벤트를 받을 수 있습니다.

**구현:**
```typescript
const NotificationContext = createContext<{
  lastEventId: string | null;
  setLastEventId: (id: string) => void;
}>(null!);

export function NotificationProvider({ children }: { children: React.ReactNode }) {
  const { accessToken } = useAuth();
  const [lastEventId, setLastEventId] = useState<string | null>(() => {
    // 로컬 스토리지에서 복구
    return localStorage.getItem('notificationLastEventId');
  });

  useEffect(() => {
    if (!accessToken) return;

    const client = new NotificationClient({
      initialDelayMs: 1000,
      maxDelayMs: 30000,
      backoffMultiplier: 2,
    });

    client.on('notification', (notification) => {
      // 알림 처리
    });

    client.on('lastEventIdUpdated', (newId) => {
      setLastEventId(newId);
      localStorage.setItem('notificationLastEventId', newId);
    });

    client.connect(accessToken);

    return () => client.disconnect();
  }, [accessToken]);

  return (
    <NotificationContext.Provider value={{ lastEventId, setLastEventId }}>
      {children}
    </NotificationContext.Provider>
  );
}
```

---

## 알림 타입 및 라우팅

각 알림 타입에 따라 클릭 시 이동할 페이지가 달라집니다.

### 알림 타입 매핑표

| 타입 | 설명 | referenceId | 라우팅 페이지 |
|------|------|------------|------------|
| `NEW_COMMENT` | 게시글에 댓글 | 게시글 ID | `/posts/{referenceId}` |
| `NEW_REPLY` | 댓글에 답글 | 게시글 ID | `/posts/{referenceId}` (댓글 스크롤) |
| `NEW_POST_REACTION` | 게시글 반응 | 게시글 ID | `/posts/{referenceId}` |
| `NEW_COMMENT_REACTION` | 댓글 반응 | 댓글 ID | `/posts/{postId}` (댓글 스크롤) |
| `NEW_SCRAP` | 게시글 스크랩 | 게시글 ID | `/posts/{referenceId}` |
| `VERIFICATION_APPROVED` | 치료사 인증 승인 | - | `/my-profile` |
| `VERIFICATION_REJECTED` | 치료사 인증 거절 | - | `/my-profile` |

### 라우팅 유틸리티

```typescript
import { useNavigate } from 'react-router-dom';

type NotificationType =
  | 'NEW_COMMENT'
  | 'NEW_REPLY'
  | 'NEW_POST_REACTION'
  | 'NEW_COMMENT_REACTION'
  | 'NEW_SCRAP'
  | 'VERIFICATION_APPROVED'
  | 'VERIFICATION_REJECTED';

export function getNotificationRoute(
  type: NotificationType,
  referenceId?: number
): string {
  switch (type) {
    case 'NEW_COMMENT':
    case 'NEW_POST_REACTION':
    case 'NEW_SCRAP':
      return `/posts/${referenceId}`;
    
    case 'NEW_REPLY':
    case 'NEW_COMMENT_REACTION':
      // referenceId는 댓글 ID, 게시글 ID를 별도로 알아야 함
      // 또는 알림 클릭 시 상세 조회 후 결정
      return `/posts/${referenceId}`;
    
    case 'VERIFICATION_APPROVED':
    case 'VERIFICATION_REJECTED':
      return '/my-profile';
    
    default:
      return '/notifications';
  }
}

export function NotificationItem({ notification }: { notification: NotificationResponse }) {
  const navigate = useNavigate();

  const handleClick = () => {
    const route = getNotificationRoute(notification.type, notification.referenceId);
    navigate(route);
  };

  return (
    <div onClick={handleClick} style={{ cursor: 'pointer' }}>
      <p className={notification.read ? 'read' : 'unread'}>{notification.content}</p>
      <time>{new Date(notification.createdAt).toLocaleString('ko-KR')}</time>
    </div>
  );
}
```

---

## React 통합 예시

### 1. 커스텀 훅: useNotification

```typescript
import { useState, useEffect, useCallback, useRef } from 'react';

export interface NotificationResponse {
  id: number;
  type: string;
  content: string;
  referenceId?: number;
  senderId?: number;
  senderNickname?: string;
  read: boolean;
  readAt?: string;
  createdAt: string;
}

interface UseNotificationConfig {
  accessToken: string;
  onNewNotification?: (notification: NotificationResponse) => void;
}

export function useNotification(config: UseNotificationConfig) {
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const lastEventIdRef = useRef<string | null>(null);

  const fetchUnreadCount = useCallback(async () => {
    try {
      const response = await fetch('/api/v1/notifications/unread-count', {
        headers: { Authorization: `Bearer ${config.accessToken}` },
      });
      const data = await response.json();
      setUnreadCount(data.data.count);
    } catch (err) {
      console.error('미읽은 수 조회 실패:', err);
    }
  }, [config.accessToken]);

  const markAsRead = useCallback(
    async (notificationId: number) => {
      try {
        await fetch(`/api/v1/notifications/${notificationId}/read`, {
          method: 'PATCH',
          headers: { Authorization: `Bearer ${config.accessToken}` },
        });

        setNotifications((prev) =>
          prev.map((n) =>
            n.id === notificationId ? { ...n, read: true, readAt: new Date().toISOString() } : n
          )
        );

        await fetchUnreadCount();
      } catch (err) {
        console.error('읽음 처리 실패:', err);
      }
    },
    [config.accessToken, fetchUnreadCount]
  );

  const markAllAsRead = useCallback(async () => {
    try {
      await fetch('/api/v1/notifications/read-all', {
        method: 'PATCH',
        headers: { Authorization: `Bearer ${config.accessToken}` },
      });

      setNotifications((prev) =>
        prev.map((n) => ({ ...n, read: true, readAt: new Date().toISOString() }))
      );
      setUnreadCount(0);
    } catch (err) {
      console.error('전체 읽음 처리 실패:', err);
    }
  }, [config.accessToken]);

  const deleteNotification = useCallback(
    async (notificationId: number) => {
      try {
        await fetch(`/api/v1/notifications/${notificationId}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${config.accessToken}` },
        });

        setNotifications((prev) => prev.filter((n) => n.id !== notificationId));
        await fetchUnreadCount();
      } catch (err) {
        console.error('삭제 실패:', err);
      }
    },
    [config.accessToken, fetchUnreadCount]
  );

  useEffect(() => {
    if (!config.accessToken) return;

    fetchUnreadCount();

    let reconnectDelay = 1000;
    const maxDelay = 30000;
    let isRunning = true;

    const connect = async () => {
      try {
        const headers: Record<string, string> = {
          Authorization: `Bearer ${config.accessToken}`,
        };

        if (lastEventIdRef.current) {
          headers['Last-Event-ID'] = lastEventIdRef.current;
        }

        const response = await fetch('/api/v1/notifications/subscribe', {
          method: 'GET',
          headers,
        });

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }

        setIsConnected(true);
        reconnectDelay = 1000;

        const reader = response.body?.getReader();
        if (!reader) throw new Error('ReadableStream 미지원');

        const decoder = new TextDecoder();
        let buffer = '';
        let currentEvent: { id?: string; event?: string; data?: string } = {};

        while (isRunning) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          for (const line of lines) {
            if (line.trim() === '') {
              if (currentEvent.event === 'notification' && currentEvent.data) {
                try {
                  const notification = JSON.parse(currentEvent.data) as NotificationResponse;
                  if (currentEvent.id) {
                    lastEventIdRef.current = currentEvent.id;
                  }
                  setNotifications((prev) => [notification, ...prev]);
                  config.onNewNotification?.(notification);
                  await fetchUnreadCount();
                } catch (err) {
                  console.error('알림 파싱 실패:', err);
                }
              }
              currentEvent = {};
              continue;
            }

            if (line.startsWith(':')) continue;

            const colonIndex = line.indexOf(':');
            if (colonIndex === -1) continue;

            const key = line.substring(0, colonIndex);
            const value = line.substring(colonIndex + 2);

            if (key === 'id') currentEvent.id = value;
            else if (key === 'event') currentEvent.event = value;
            else if (key === 'data') currentEvent.data = value;
          }
        }
      } catch (err) {
        setIsConnected(false);
        setError(err instanceof Error ? err : new Error(String(err)));

        if (isRunning) {
          console.warn(`재연결 예정 (${reconnectDelay}ms):`, err);
          await new Promise((resolve) => setTimeout(resolve, reconnectDelay));
          reconnectDelay = Math.min(reconnectDelay * 2, maxDelay);
          await connect();
        }
      }
    };

    connect();

    return () => {
      isRunning = false;
    };
  }, [config.accessToken, config.onNewNotification, fetchUnreadCount]);

  return {
    notifications,
    unreadCount,
    isConnected,
    error,
    markAsRead,
    markAllAsRead,
    deleteNotification,
  };
}
```

### 2. Context Provider: NotificationProvider

```typescript
import React, { createContext, useContext, ReactNode } from 'react';
import { useNotification, NotificationResponse } from './useNotification';

interface NotificationContextType {
  notifications: NotificationResponse[];
  unreadCount: number;
  isConnected: boolean;
  error: Error | null;
  markAsRead: (id: number) => Promise<void>;
  markAllAsRead: () => Promise<void>;
  deleteNotification: (id: number) => Promise<void>;
}

const NotificationContext = createContext<NotificationContextType | null>(null);

export function NotificationProvider({
  children,
  accessToken,
  onNewNotification,
}: {
  children: ReactNode;
  accessToken: string;
  onNewNotification?: (notification: NotificationResponse) => void;
}) {
  const notification = useNotification({
    accessToken,
    onNewNotification,
  });

  return (
    <NotificationContext.Provider value={notification}>
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotifications() {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error('useNotifications must be used within NotificationProvider');
  }
  return context;
}
```

### 3. 알림 배지 컴포넌트

```typescript
import { useNotifications } from './NotificationProvider';

export function NotificationBadge() {
  const { unreadCount, isConnected } = useNotifications();

  return (
    <div style={{ position: 'relative' }}>
      <button>알림</button>
      {unreadCount > 0 && (
        <span
          style={{
            position: 'absolute',
            top: '-8px',
            right: '-8px',
            backgroundColor: '#ef4444',
            color: 'white',
            borderRadius: '50%',
            width: '24px',
            height: '24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '12px',
            fontWeight: 'bold',
          }}
        >
          {unreadCount > 99 ? '99+' : unreadCount}
        </span>
      )}
      {!isConnected && (
        <span
          style={{
            position: 'absolute',
            top: '0px',
            right: '0px',
            width: '8px',
            height: '8px',
            backgroundColor: '#fbbf24',
            borderRadius: '50%',
          }}
          title="재연결 중"
        />
      )}
    </div>
  );
}
```

### 4. 알림 리스트 컴포넌트

```typescript
import { useNotifications } from './NotificationProvider';
import { getNotificationRoute } from './utils';
import { useNavigate } from 'react-router-dom';

export function NotificationList() {
  const { notifications, markAsRead, deleteNotification } = useNotifications();
  const navigate = useNavigate();

  const handleNotificationClick = (notification: NotificationResponse) => {
    if (!notification.read) {
      markAsRead(notification.id);
    }

    const route = getNotificationRoute(notification.type, notification.referenceId);
    navigate(route);
  };

  return (
    <div>
      <h2>알림</h2>
      {notifications.length === 0 ? (
        <p>알림이 없습니다.</p>
      ) : (
        <ul>
          {notifications.map((notification) => (
            <li
              key={notification.id}
              style={{
                padding: '12px',
                borderBottom: '1px solid #e5e7eb',
                backgroundColor: notification.read ? '#fff' : '#f3f4f6',
                cursor: 'pointer',
              }}
              onClick={() => handleNotificationClick(notification)}
            >
              <div>
                <p style={{ fontWeight: notification.read ? 'normal' : 'bold' }}>
                  {notification.content}
                </p>
                <time style={{ fontSize: '12px', color: '#666' }}>
                  {new Date(notification.createdAt).toLocaleString('ko-KR')}
                </time>
              </div>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  deleteNotification(notification.id);
                }}
              >
                삭제
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
```

### 5. 앱 통합

```typescript
import { NotificationProvider } from './contexts/NotificationProvider';
import { useAuth } from './hooks/useAuth';

function App() {
  const { accessToken } = useAuth();

  if (!accessToken) {
    return <div>로그인이 필요합니다.</div>;
  }

  return (
    <NotificationProvider
      accessToken={accessToken}
      onNewNotification={(notification) => {
        // 토스트 알림 등 추가 처리
        console.log('새 알림:', notification);
      }}
    >
      <Header />
      <Routes>
        <Route path="/posts/:id" element={<PostDetail />} />
        <Route path="/notifications" element={<NotificationList />} />
      </Routes>
    </NotificationProvider>
  );
}
```

---

## 에러 처리

### HTTP 에러

**401 Unauthorized**
- 원인: 토큰 만료, 토큰 없음
- 처리: 재로그인, 토큰 갱신

**500 SSE_CONNECTION_ERROR**
- 원인: 초기 SSE 연결 실패
- 처리: 지수 백오프로 재연결

### 네트워크 에러

**연결 끊김**
- 원인: 네트워크 오류, 서버 다운
- 처리: 자동 재연결 (지수 백오프)

**타임아웃**
- 원인: 서버 응답 없음 (30분 이상)
- 처리: 자동 재연결 + Last-Event-ID로 유실 복구

### 에러 처리 예시

```typescript
export function useNotification(config: UseNotificationConfig) {
  const [error, setError] = useState<Error | null>(null);

  const handleError = (err: unknown) => {
    if (err instanceof Error) {
      if (err.message.includes('401')) {
        setError(new Error('토큰이 만료되었습니다. 다시 로그인해주세요.'));
        // 재로그인 흐름
      } else if (err.message.includes('500')) {
        setError(new Error('서버 오류로 알림을 받을 수 없습니다.'));
      } else {
        setError(err);
      }
    }
  };

  return { error };
}

// 컴포넌트에서 사용
export function NotificationContainer() {
  const { error } = useNotifications();

  if (error) {
    return (
      <div style={{ color: 'red', padding: '12px' }}>
        <strong>오류:</strong> {error.message}
      </div>
    );
  }

  return <NotificationList />;
}
```

---

## 토큰 만료 처리

SSE 연결 중 액세스 토큰이 만료되면 401 응답을 받습니다. 이 경우 토큰을 갱신하고 재연결해야 합니다.

### 구현 전략

```typescript
interface AuthContext {
  accessToken: string | null;
  refreshAccessToken: () => Promise<string>;
  logout: () => void;
}

export function useNotification(config: UseNotificationConfig & { auth: AuthContext }) {
  useEffect(() => {
    const connect = async () => {
      try {
        const response = await fetch('/api/v1/notifications/subscribe', {
          headers: {
            Authorization: `Bearer ${config.accessToken}`,
            'Last-Event-ID': lastEventIdRef.current || '',
          },
        });

        // 401 응답
        if (response.status === 401) {
          try {
            // 토큰 갱신 시도
            const newToken = await config.auth.refreshAccessToken();
            // 새 토큰으로 재연결
            await connect();
            return;
          } catch (refreshErr) {
            // 갱신 실패 → 로그아웃
            config.auth.logout();
            return;
          }
        }

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }

        // ... SSE 처리
      } catch (err) {
        // 재연결 로직
      }
    };

    connect();
  }, [config.accessToken]);
}
```

---

## 주의사항 및 베스트 프랙티스

### 보안

1. **HTTPS 필수**: 토큰이 네트워크를 통해 전송되므로 항상 HTTPS 사용
2. **Authorization 헤더 사용**: 쿼리 파라미터로 토큰 전달하지 않기 (가능하면)
3. **토큰 갱신**: 액세스 토큰 만료 시 자동 갱신

### 성능

1. **최대 5개 동시 연결**: 일반적으로 1개만 필요. 탭/창이 여러 개인 경우 공유 Worker 고려
2. **30초 하트비트**: 서버 자원 절약 (설정 변경 불가)
3. **50건 이벤트 캐시**: 15초 이상 네트워크 오류 시 일부 이벤트 유실 가능

### 사용자 경험

1. **시각적 피드백**: 연결 상태 표시 (아이콘 색상, 배지 등)
2. **알림음**: 소리로 알림 (기본값으로 음소거)
3. **배지 업데이트**: 미읽은 수 배지를 실시간으로 업데이트
4. **푸시 알림**: SSE와 병행하여 PWA 푸시 알림 사용 검토

### 다중 탭/창 처리

브라우저 탭이 여러 개인 경우 각 탭이 SSE 연결을 독립적으로 유지합니다. 이를 최적화하려면:

```typescript
// Broadcast Channel을 사용한 탭 간 통신
const channel = new BroadcastChannel('notifications');

channel.onmessage = (event) => {
  if (event.data.type === 'NEW_NOTIFICATION') {
    // 다른 탭에서 받은 알림 처리
    setNotifications((prev) => [event.data.notification, ...prev]);
  }
};

// SSE에서 알림 수신 시 다른 탭에 알림
if (notification) {
  channel.postMessage({
    type: 'NEW_NOTIFICATION',
    notification,
  });
}
```

### 모바일 환경

1. **백그라운드 실행**: 모바일 브라우저는 탭 비활성화 시 SSE 연결을 정지할 수 있음
2. **푸시 알림**: PWA Push API 또는 FCM 등과 함께 사용
3. **배터리 절약**: 최소 재연결 지연 시간을 너무 짧게 하지 않기

### 로깅 및 모니터링

```typescript
const notificationClient = new NotificationClient({
  initialDelayMs: 1000,
  maxDelayMs: 30000,
  backoffMultiplier: 2,
});

notificationClient.on('connect', () => {
  console.log('[알림] SSE 연결 성공');
  // 분석 이벤트 전송
  analytics.logEvent('notification_sse_connected');
});

notificationClient.on('notification', (notification) => {
  console.log('[알림] 새 알림:', notification);
  analytics.logEvent('notification_received', {
    type: notification.type,
  });
});

notificationClient.on('error', (error) => {
  console.error('[알림] 에러:', error);
  analytics.logEvent('notification_error', {
    message: error.message,
  });
});

notificationClient.on('reconnect', (attempt, delayMs) => {
  console.warn(`[알림] 재연결 시도 #${attempt} (${delayMs}ms 후)`);
  analytics.logEvent('notification_reconnect_attempt', {
    attempt,
    delayMs,
  });
});
```

### 테스트

```typescript
// 모의 SSE 클라이언트 (테스트용)
class MockNotificationClient {
  private eventHandlers: Map<string, Function[]> = new Map();

  on(event: string, handler: Function) {
    if (!this.eventHandlers.has(event)) {
      this.eventHandlers.set(event, []);
    }
    this.eventHandlers.get(event)!.push(handler);
  }

  simulateNotification(notification: NotificationResponse) {
    const handlers = this.eventHandlers.get('notification') || [];
    handlers.forEach((handler) => handler(notification));
  }

  simulateError(error: Error) {
    const handlers = this.eventHandlers.get('error') || [];
    handlers.forEach((handler) => handler(error));
  }
}

// 테스트
describe('NotificationProvider', () => {
  it('should update unread count on new notification', () => {
    const { getByText } = render(<NotificationBadge />);

    mockClient.simulateNotification({
      id: 1,
      type: 'NEW_COMMENT',
      content: 'test',
      read: false,
      createdAt: new Date().toISOString(),
    });

    expect(getByText('1')).toBeInTheDocument();
  });
});
```

---

## 참고 자료

- [Server-Sent Events (MDN)](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)
- [EventSource API (MDN)](https://developer.mozilla.org/en-US/docs/Web/API/EventSource)
- [ReadableStream (MDN)](https://developer.mozilla.org/en-US/docs/Web/API/ReadableStream)
- [Broadcast Channel API (MDN)](https://developer.mozilla.org/en-US/docs/Web/API/Broadcast_Channel_API)

---

## 문제 해결 (Troubleshooting)

**Q: SSE 연결이 자꾸 끊깁니다.**
A: 네트워크 환경, 방화벽, 프록시에서 긴 연결을 종료할 수 있습니다. 지수 백오프 재연결과 하트비트를 통해 자동 복구되도록 설정되어 있습니다. 서버 로그에서 연결 상태를 확인하세요.

**Q: 토큰 갱신 후 SSE 연결도 갱신해야 하나요?**
A: 예, 기존 연결을 종료하고 새 토큰으로 재연결해야 합니다. 401 응답을 받으면 자동으로 처리됩니다.

**Q: 다중 탭에서 중복 알림을 받습니다.**
A: 각 탭이 독립적인 SSE 연결을 유지하므로 같은 알림을 여러 번 받을 수 있습니다. Broadcast Channel을 사용하여 탭 간 통신으로 해결할 수 있습니다.

**Q: 모바일에서 SSE가 작동하지 않습니다.**
A: 모바일 브라우저의 백그라운드 실행 정책 때문에 탭 비활성화 시 연결이 정지될 수 있습니다. PWA 푸시 알림 사용을 권장합니다.

**Q: 이벤트 캐시 50건을 초과하면 어떻게 되나요?**
A: 가장 오래된 이벤트부터 삭제됩니다. 15초 이상 네트워크 오류 시 일부 이벤트를 받지 못할 수 있습니다. 재연결 후 알림 목록 API로 최신 알림을 조회하세요.

---

## 지원

문제가 있으면 다음을 확인하세요:
- 백엔드 로그: `/var/log/therapist-community.log` (또는 로컬: `./logs/app.log`)
- Swagger 문서: `http://localhost:8080/swagger-ui/index.html`
- 아키텍처 문서: `docs/architecture/NOTIFICATION.md`