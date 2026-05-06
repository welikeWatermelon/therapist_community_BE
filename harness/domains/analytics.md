# Analytics Domain

- Path: `src/main/java/com/therapyCommunity_Vol1/backend/analytics`

## 책임

- 사용자 행동 이벤트 적재, 파티션 관리, 시간 단위/일 단위 집계를 맡는다.

## 진입점

- `UserEventPublisher`
- `UserEventListener`
- `AnalyticsScheduler`
- `UserEventPartitionService`, `PostHourlyAggregationService`, `TherapistExpertiseAggregationService`

## 주요 모델

- `UserEvent`
- `UserEventType`
- `EventTargetType`
- `AggregationProgress`

## 연동

- `post`, `comment`, `reaction`, `scrap` 같은 도메인이 publisher를 통해 이벤트를 보낸다.
- listener는 `REQUIRES_NEW` 로 별도 트랜잭션에서 저장한다.
- 자세한 배경은 [docs/architecture/ANALYTICS.md](../../docs/architecture/ANALYTICS.md) 참고.

## 변경 체크

- 원본 비즈니스 트랜잭션 실패/성공과 analytics 저장을 섞지 않는다.
- 집계 스케줄 수정 시 중복 실행과 progress cursor를 같이 본다.
