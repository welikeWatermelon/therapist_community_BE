# Frontend Handoff Guide

프론트 팀에 전달할 때 이 문서 순서대로 진행하면 됩니다.

## 1) 로컬에서 Swagger 문서 열기

백엔드 실행:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Swagger UI:

- `http://localhost:8080/swagger-ui/index.html`

OpenAPI JSON:

- `http://localhost:8080/v3/api-docs`

OpenAPI 파일로 저장:

```bash
mkdir -p docs
curl -s http://localhost:8080/v3/api-docs > docs/openapi-local.json
```

## 2) 프론트에 꼭 전달할 정보

아래 5가지는 반드시 같이 전달합니다.

1. Swagger URL: `http://localhost:8080/swagger-ui/index.html`
2. OpenAPI JSON 파일: `docs/openapi-local.json`
3. 인증 방식: `Authorization: Bearer <accessToken>`
4. 테스트 계정: `USER`, `THERAPIST`, `ADMIN`
5. 에러 규칙: 인증 없음/실패 `401`, 권한 부족 `403`

## 3) 치료사 신청 API 주의사항

엔드포인트:

- `POST /api/v1/therapist-verifications`

요청 형식:

- `multipart/form-data`
- 필드명 정확히:
  - `licenseCode` (문자열)
  - `licenseImage` (파일)

curl 예시:

```bash
curl -X POST 'http://localhost:8080/api/v1/therapist-verifications' \
  -H 'Authorization: Bearer <ACCESS_TOKEN>' \
  -F 'licenseCode=ABC-1234' \
  -F 'licenseImage=@/absolute/path/to/image.png'
```

## 4) 이미지 조회 API 주의사항

엔드포인트:

- `GET /api/v1/therapist-verifications/me/image`

응답은 `ApiResponse` JSON이 아니라 `binary` 파일 응답입니다.

- `Content-Type`: `image/png`, `image/jpeg` 등
- body: 파일 바이트

프론트에서는 JSON 파싱이 아니라 이미지/파일 응답 처리로 받아야 합니다.

## 5) Swagger에서 인증이 안 붙는 경우 체크

1. Swagger 우상단 `Authorize` 눌러 토큰 입력
2. 입력값은 토큰 문자열만 넣기 (`Bearer` 접두사 직접 입력하지 않음)
3. 그래도 401이면 토큰 만료 여부 확인 후 재로그인

## 6) AWS 테스트 서버 공유 체크리스트

배포 후 프론트에 아래 항목 전달:

1. Base URL (예: `https://staging-api.example.com`)
2. Swagger URL (예: `https://staging-api.example.com/swagger-ui/index.html`)
3. OpenAPI URL (예: `https://staging-api.example.com/v3/api-docs`)
4. 테스트 계정 3종 (USER/THERAPIST/ADMIN)
5. CORS 허용 도메인 목록
6. 파일 업로드 용량 제한

## 7) 프론트 전달용 메시지 템플릿

```text
[Backend Handoff]
- Base URL: <BASE_URL>
- Swagger: <SWAGGER_URL>
- OpenAPI JSON: <OPENAPI_URL 또는 파일>
- Auth: Authorization: Bearer <accessToken>
- Test Accounts:
  - USER: <id/pw>
  - THERAPIST: <id/pw>
  - ADMIN: <id/pw>

[주의]
- POST /api/v1/therapist-verifications 는 multipart/form-data 입니다.
  - licenseCode: string
  - licenseImage: file
- GET /api/v1/therapist-verifications/me/image 는 JSON이 아니라 binary 응답입니다.
```
