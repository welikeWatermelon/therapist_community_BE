# Application MyPage

- Path: `src/main/java/com/therapyCommunity_Vol1/backend/application/mypage`

## 책임

- 마이페이지에서 필요한 user/post/comment 흐름을 한 곳에서 조합한다.

## 진입점

- `MyPageFacade`
- 실제 HTTP endpoint는 `user/UserController` 아래 `/api/v1/me/**`

## 주요 모델

- `MyCommentResponse`

## 연동

- `user`, `post`, `comment` 서비스만 호출하는 얇은 orchestration 레이어다.

## 변경 체크

- business rule은 facade보다 각 도메인 service에 남겨둔다.
- 마이페이지 전용 DTO 가공에서 삭제된 댓글 placeholder 규칙을 유지한다.
