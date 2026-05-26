# Frontend Handoff Guide

프론트 팀 전달 기준 문서입니다. 프론트 전달 관련 내용은 이 문서 하나만 기준으로 관리합니다.

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

## 8) 이미지 업로드 — confirm 멱등성과 FE 재시도 가이드

이미지 업로드는 3단계입니다: `init` → **브라우저가 S3로 직접 PUT** → `confirm`.
가운데 PUT은 백엔드를 거치지 않으므로, **PUT 실패는 백엔드 로그에 남지 않습니다.** FE에서 잡아주지 않으면 원인 추적이 불가능합니다(실제로 운영 이미지 누락 컴플레인의 원인이 FE의 빈 `catch{}`로 에러를 삼킨 것이었습니다).

### 반드시 적용 (2가지)

1. **에러를 삼키지 말 것** — 빈 `catch {}` 금지. 최소한 `status` + S3 응답 본문(XML)을 로깅/리포트.
   ```ts
   } catch (e) {
     console.error('upload failed', { step, storedKey, status: e?.response?.status, body: e?.response?.data });
     // (가능하면 Sentry 등으로 리포트)
   }
   ```
2. **재시도** — 일시적 PUT 실패를 자동 복구. 단 **반드시 같은 `storedKey`를 재사용**(init 재호출 금지).
   - PUT 실패 → PUT 재시도 (2~3회, 지수 백오프). `uploadUrl`은 그대로 재사용.
   - confirm 실패 → confirm 재시도. **백엔드 confirm은 멱등**이라 같은 `storedKey`로 다시 불러도 안전(중복 이미지·에러 없음, 첫 성공 결과를 그대로 반환).
   - **예외**: presigned URL은 5분 만료. 만료(`403 ... expired`) 시에만 `init`을 재발급한 뒤 재시도.

### 왜 같은 storedKey 재사용이 안전한가 (백엔드 보장)

백엔드는 `storedKey`에서 **결정적으로** `finalKey`(`post-images/{filename}`)를 만들고, `stored_path`에 **유니크 제약**을 둡니다. 그래서:
- 성공 후 응답만 유실된 재시도 → 백엔드가 기존 레코드를 찾아 **같은 결과 반환**(에러 아님).
- 거의 동시 중복 confirm → 유니크 제약으로 하나만 저장, 나머지는 기존 결과 반환.
- **시간복잡도**: 멱등 판정 `findByStoredPath` = `stored_path` 유니크 인덱스로 **O(log n)**, 결정적 `finalKey` 생성 **O(1)** — 추가 비용 무시 가능.

(설계 상세는 백엔드 `UploadConfirmService` + Flyway `V47`. 단, 이 멱등성은 **FE가 같은 storedKey로 재시도해야** 실효가 납니다.)

### UX 권장

현재 글은 먼저 저장되고 이미지가 뒤에 붙는 순서라, 업로드 실패 시 "등록됐지만 첨부 실패" alert만 뜹니다. **해당 이미지만 재시도하는 버튼/플로우** 제공을 권장합니다.
