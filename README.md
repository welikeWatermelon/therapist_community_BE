
# Builders MVP Backend

Spring Boot 3 기반 백엔드 API 서버입니다.

## Quick Start

### 1) Run (local profile)

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 2) API Docs

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### 3) Build

```bash
./gradlew clean build
```

## Main Docs

- Docs index: [docs/README.md](docs/README.md)
- API spec: [docs/API_SPEC.md](docs/API_SPEC.md)
- Branch operations guide: [docs/BRANCH_OPERATIONS_GUIDE.md](docs/BRANCH_OPERATIONS_GUIDE.md)
- Cloud handoff package: [docs/CLOUD_HANDOFF_PACKAGE.md](docs/CLOUD_HANDOFF_PACKAGE.md)
- Frontend handoff: [docs/FRONTEND_HANDOFF.md](docs/FRONTEND_HANDOFF.md)
- Server check runbook: [docs/SERVER_CHECK_RUNBOOK.md](docs/SERVER_CHECK_RUNBOOK.md)

## Runtime Notes

- 인증: `Authorization: Bearer <accessToken>`
- 치료사 신청 API는 `multipart/form-data` 사용
- 이미지 조회 API는 JSON(`ApiResponse`)이 아닌 binary 응답
