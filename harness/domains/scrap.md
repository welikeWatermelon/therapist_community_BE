# Scrap Domain

- Path: `src/main/java/com/therapyCommunity_Vol1/backend/scrap`

## 책임

- 게시글 북마크 추가/삭제, 상태 조회, 내 스크랩 목록을 맡는다.

## 진입점

- `ScrapController`
- `ScrapService`

## 주요 모델

- `TherapyPostScrap`

## 연동

- `post` 존재 여부를 확인하고 soft delete된 글은 제외한다.
- `notification` 과 `analytics` 이벤트를 발행한다.
- 게시글 인기 점수 재계산을 트리거한다.

## 변경 체크

- 한 사용자당 한 글 하나의 scrap만 허용하는 제약을 유지한다.
- POST/DELETE가 idempotent 하게 동작하는지 본다.
