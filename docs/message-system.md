# 쪽지(Message) 시스템

## 개요
1:1 쪽지 발송 및 관리자 공지 쪽지 기능. 기존 SSE 알림 인프라를 재사용하여 실시간 알림 지원.

## DB 스키마

### messages 테이블 (V45 + V46)
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
| deleted_at | TIMESTAMP | 양쪽 삭제 시 soft delete 타임스탬프 (V46) |
| created_at | TIMESTAMP | 생성일 |
| updated_at | TIMESTAMP | 수정일 |

### 인덱스
- `idx_messages_receiver_inbox` — 받은 쪽지함 (partial: deleted_by_receiver = FALSE)
- `idx_messages_sender_outbox` — 보낸 쪽지함 (partial: deleted_by_sender = FALSE)
- `idx_messages_receiver_unread` — 안읽은 쪽지 수 (partial: is_read = FALSE AND deleted_by_receiver = FALSE)
- `idx_messages_broadcast_id` — 공지 그룹 조회 (partial: broadcast_id IS NOT NULL)
- `idx_messages_soft_deleted` — 배치 정리용 (partial: deleted_at IS NOT NULL) (V46)

### 제약조건
- `chk_no_self_message` — CHECK (sender_id != receiver_id) (V46)

## API 엔드포인트

| Method | Path | 설명 | 권한 | 응답 코드 |
|--------|------|------|------|-----------|
| POST | /api/v1/messages | 1:1 쪽지 발송 | authenticated | 201 Created |
| POST | /api/v1/admin/messages/broadcast | 관리자 공지 쪽지 | ADMIN | 201 Created |
| GET | /api/v1/me/messages/received | 받은 쪽지함 (페이징) | authenticated | 200 OK |
| GET | /api/v1/me/messages/sent | 보낸 쪽지함 (페이징) | authenticated | 200 OK |
| GET | /api/v1/messages/{messageId} | 쪽지 상세 (자동 읽음) | authenticated | 200 OK |
| DELETE | /api/v1/messages/{messageId} | 쪽지 삭제 (호출자 측만) | authenticated | 204 No Content |
| GET | /api/v1/me/messages/unread-count | 안읽은 쪽지 수 | authenticated | 200 OK |

## 설계 결정

### 관리자 공지: N rows 개별 생성
- 수신자 수만큼 개별 row 생성 (batch insert, `hibernate.jdbc.batch_size=50`)
- `broadcast_id` UUID로 공지 그룹 태깅
- 수신자별 개별 알림 발행 (각 쪽지 ID를 referenceId로 전달하여 딥링크 지원)
- 공지 발송 응답에 `broadcastId`와 `recipientCount` 포함
- 장점: 쿼리 단순, 읽음/삭제 독립 관리, UNION 불필요
- 트레이드오프: 수천 명 규모에서 저장 비용 무시 가능

### 독립 삭제 + soft delete
- `deleted_by_sender` / `deleted_by_receiver` 두 boolean 플래그로 독립 삭제
- 한쪽이 삭제해도 상대방 쪽지함에 영향 없음
- 양쪽 모두 삭제 시(`isFullyDeleted()`) `deleted_at` 타임스탬프 설정 (soft delete)
- soft delete된 row는 추후 배치 정리 대상 (스케줄러 미구현, 추후 과제)

### SSE 알림 재사용
- `NotificationType.NEW_MESSAGE` 추가
- 기존 `NotificationEventListener` → `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` 흐름 그대로 활용

### 보안
- 서비스 레벨 ADMIN 권한 검증 (SecurityConfig + 이중 방어)
- 자기 자신 발송 차단 (서비스 검증 + DB CHECK 제약)
- 탈퇴 사용자 발송/수신 차단
- `targetRole`이 지정되면 해당 역할에게만 발송 (ADMIN 포함 가능)
- `targetRole`이 null이면 USER + THERAPIST에게 발송 (ADMIN 제외)
- 페이징 size 제한 (@Min(1) @Max(100) + 서비스 레벨 Math.max/Math.min 보정)

## 제한사항 (초기 버전)
- 텍스트만 지원 (이미지/파일 첨부 미지원)
- 제목 없음 (본문만)
- 차단/스팸 기능 미구현
- Rate limiting 미구현 (프로젝트 전반 과제)
- soft delete된 row 물리 삭제 배치 미구현
