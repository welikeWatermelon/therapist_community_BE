# Comment Domain

- Path: `src/main/java/com/therapyCommunity_Vol1/backend/comment`

## 책임

- 게시글 댓글, 대댓글, 수정, soft delete, 스레드 조립을 맡는다.

## 진입점

- `CommentController`
- `CommentService`
- `CommentThreadAssembler`

## 주요 모델

- `TherapyPostComment`

## 연동

- `post` 도메인 글 존재와 접근 가능 여부를 전제로 동작한다.
- `notification` 으로 댓글/대댓글 이벤트를 보낸다.
- `reaction` 은 댓글 반응 상태 집계를 붙인다.

## 변경 체크

- depth 2 제한을 깨는 경로가 생기지 않게 본다.
- 삭제된 댓글 placeholder 정책을 응답 DTO와 함께 유지한다.
- 댓글 수정/삭제 권한은 author or admin 검증을 같이 본다.
