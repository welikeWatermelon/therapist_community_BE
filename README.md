
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

- Cloud quick start: [docs/cloud/README.md](docs/cloud/README.md)
- Frontend handoff: [docs/FRONTEND_HANDOFF.md](docs/FRONTEND_HANDOFF.md)
- Frontend compact share: [docs/FRONTEND_SHARE_COMPACT.md](docs/FRONTEND_SHARE_COMPACT.md)
- Systemd deployment: [docs/SYSTEMD_DEPLOYMENT.md](docs/SYSTEMD_DEPLOYMENT.md)
- Cloud handoff package: [docs/CLOUD_HANDOFF_PACKAGE.md](docs/CLOUD_HANDOFF_PACKAGE.md)

## Runtime Notes

- 인증: `Authorization: Bearer <accessToken>`
- 치료사 신청 API는 `multipart/form-data` 사용
- 이미지 조회 API는 JSON(`ApiResponse`)이 아닌 binary 응답
