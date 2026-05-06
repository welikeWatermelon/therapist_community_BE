# File Domain

- Path: `src/main/java/com/therapyCommunity_Vol1/backend/file`

## 책임

- 업로드 대상별 저장 API와 resource/stream/presigned URL 추상화를 제공한다.

## 진입점

- `FileStorageService`
- `LocalFileStorageService`
- `S3FileStorageService`

## 주요 모델

- `StoredFileInfo`
- `StoredFileResource`

## 연동

- `user`, `therapist`, `post`, `knowledge`, `meta` 가 모두 이 인터페이스를 통해 파일에 접근한다.
- 로컬 구현은 `uploads/`, S3 구현은 presigned GET fallback을 지원한다.

## 변경 체크

- 파일 타입별 검증 규칙을 호출부와 저장소 구현 양쪽에서 깨지지 않게 본다.
- download endpoint와 `presignGet(...)` fallback 의미를 같이 유지한다.
