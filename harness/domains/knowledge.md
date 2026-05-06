# Knowledge Domain

- Path: `src/main/java/com/therapyCommunity_Vol1/backend/knowledge`

## 책임

- 관리자 지식 문서 업로드, checksum 중복 방지, 텍스트 추출, 청킹, 임베딩 적재, 재시도를 맡는다.

## 진입점

- `AdminKnowledgeController`
- `KnowledgeDocumentService`
- `KnowledgeIngestionService`
- `KnowledgeDocumentCreatedListener`

## 주요 모델

- `KnowledgeDocument`
- `KnowledgeChunk`
- `KnowledgeDocumentArtifact`

## 연동

- `file` 도메인에서 문서를 저장하고 stream으로 다시 읽는다.
- Gemini embedding client와 pgvector 컬럼을 사용한다.
- feature flag `app.knowledge.enabled` 가 꺼지면 업로드만 되고 인입은 실패 상태로 남긴다.

## 변경 체크

- 업로드 실패 시 파일 orphan cleanup이 있어야 한다.
- extractor 지원 MIME 타입과 controller 허용 확장자를 같이 맞춘다.
- 재인덱싱 시 기존 chunk/artifact 삭제와 pessimistic lock을 같이 유지한다.
