# AutoComment Domain

- Path: `src/main/java/com/therapyCommunity_Vol1/backend/autocomment`

## 책임

- 게시글 기반 AI 댓글 job 생성, RAG 검색, 초안 생성, 관리자 승인/거절을 맡는다.

## 진입점

- `PostCreatedAutoCommentListener`
- `AiCommentJobService`
- `AdminAiCommentController`
- `AiCommentReviewService`

## 주요 모델

- `PostAiCommentJob`
- `AutoCommentJobStatus`
- `ReviewStatus`
- `SourceMode`

## 연동

- `knowledge` 검색 결과를 붙여 RAG 모드 또는 fallback 모드로 초안을 만든다.
- 승인 시 `comment` 도메인으로 AI 계정 댓글을 실제 생성한다.
- 외부 호출은 Gemini chat/embedding API를 사용한다.

## 변경 체크

- private post는 job 처리 중 취소되어야 한다.
- 관리자 승인 없는 자동 댓글 게시를 추가하지 않는다.
- retry/backoff, 빈 응답 처리, 안전 프롬프트 제약을 같이 유지한다.
