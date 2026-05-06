# Reaction Domain

- Path: `src/main/java/com/therapyCommunity_Vol1/backend/reaction`

## 책임

- 게시글/댓글 반응 상태 조회와 토글, 집계, 대표 반응 계산을 맡는다.

## 진입점

- `PostReactionController`
- `CommentReactionController`
- `PostReactionService`
- `CommentReactionService`

## 주요 모델

- `TherapyPostReaction`
- `TherapyPostCommentReaction`
- `PostReactionType`, `CommentReactionType`

## 연동

- `post`, `comment` 존재 여부와 소유권 규칙을 참조한다.
- `notification` 과 `analytics` 이벤트를 발행한다.
- 게시글 반응은 `post` 인기 점수 재계산을 트리거한다.

## 변경 체크

- 토글 semantics는 생성, 삭제, 타입 변경 세 경우를 모두 유지해야 한다.
- 집계 쿼리와 대표 반응 tie-break 규칙을 같이 본다.
