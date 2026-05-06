# Post Domain

- Path: `src/main/java/com/therapyCommunity_Vol1/backend/post`

## 책임

- 게시글 CRUD, feed, relevance search, visibility, 이미지, 첨부파일, 다운로드, 인기 점수를 맡는다.

## 진입점

- `PostController`
- `PostImageController`
- `PostAttachmentController`
- `MyDownloadController`
- `PostService`

## 주요 모델

- `TherapyPost`
- `TherapyPostAttachment`, `TherapyPostImage`, `TherapyPostDownload`
- `PostSearchStrategy`, `GinTrigramSearchStrategy`, `PgVectorSearchStrategy`

## 연동

- `user` 에서 author를 읽는다.
- `comment`, `reaction`, `scrap` 과 count/인기점수 계산으로 연결된다.
- `analytics`, `autocomment`, `embedding` 이벤트를 발행한다.
- `global/cache/PostViewCountService`, `file` 저장소를 사용한다.

## 변경 체크

- soft delete, private visibility, masking 규칙을 같이 유지해야 한다.
- feed/search 커서는 정렬 키와 tie-break를 같이 바꾸지 않으면 깨진다.
- 검색 전략을 바꾸면 repository query 테스트와 score/cursor 의미를 같이 검증한다.
