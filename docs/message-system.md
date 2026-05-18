# 쪽지(Message) 시스템

## 개요
1:1 쪽지 발송 및 관리자 공지 쪽지 기능. 기존 SSE 알림 인프라를 재사용하여 실시간 알림 지원.

## DB 스키마

### messages 테이블
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGSERIAL PK | |
| sender_id | BIGINT FK(users) | 발신자 |
| receiver_id | BIGINT FK(users) | 수신자 |
| content | VARCHAR(1000) | 본문 (텍스트만) |
| is_read | BOOLEAN | 읽음 여부 |
| read_at | TIMESTAMP | 읽은 시각 |
| deleted_by_sender | BOOLEAN | 발신자 삭제 여부 |
| deleted_by_receiver | BOOLEAN | 수신자 삭제 여부 |
| broadcast_id | UUID | 공지 쪽지 그룹 (NULL이면 일반 쪽지) |
| created_at | TIMESTAMP | 생성일 |
| updated_at | TIMESTAMP | 수정일 |

### 인덱스
- `idx_messages_receiver_inbox` — 받은 쪽지함 (partial: deleted_by_receiver = FALSE)
- `idx_messages_sender_outbox` — 보낸 쪽지함 (partial: deleted_by_sender = FALSE)
- `idx_messages_receiver_unread` — 안읽은 쪽지 수 (partial: is_read = FALSE)
- `idx_messages_broadcast_id` — 공지 그룹 조회

## API 엔드포인트

| Method | Path | 설명 | 권한 |
|--------|------|------|------|
| POST | /api/v1/messages | 1:1 쪽지 발송 | authenticated |
| POST | /api/v1/admin/messages/broadcast | 관리자 공지 쪽지 | ADMIN |
| GET | /api/v1/me/messages/received | 받은 쪽지함 (페이징) | authenticated |
| GET | /api/v1/me/messages/sent | 보낸 쪽지함 (페이징) | authenticated |
| GET | /api/v1/messages/{messageId} | 쪽지 상세 (자동 읽음) | authenticated |
| DELETE | /api/v1/messages/{messageId} | 쪽지 삭제 (호출자 측만) | authenticated |
| GET | /api/v1/me/messages/unread-count | 안읽은 쪽지 수 | authenticated |

## 설계 결정

### 관리자 공지: N rows 개별 생성
- 수신자 수만큼 개별 row 생성 (batch insert)
- `broadcast_id` UUID로 공지 그룹 태깅
- 장점: 쿼리 단순, 읽음/삭제 독립 관리, UNION 불필요
- 트레이드오프: 수천 명 규모에서 저장 비용 무시 가능

### 독립 삭제
- `deleted_by_sender` / `deleted_by_receiver` 두 boolean 플래그
- 한쪽이 삭제해도 상대방 쪽지함에 영향 없음
- 기존 soft delete(`deletedAt`) 패턴은 단일 주체에 적합하므로 사용하지 않음

### SSE 알림 재사용
- `NotificationType.NEW_MESSAGE` 추가
- 기존 `NotificationEventListener` → `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` 흐름 그대로 활용

## 제한사항 (초기 버전)
- 텍스트만 지원 (이미지/파일 첨부 미지원)
- 제목 없음 (본문만)
- 차단/스팸 기능 미구현
- 자기 자신에게 쪽지 불가
