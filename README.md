
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

## Docker 개발환경

### 사전 준비
- Docker Desktop 설치 (Windows: WSL2 백엔드 권장)

### 인프라만 실행 (DB + Redis)
IDE에서 `bootRun`으로 개발할 때:
```bash
cp .env.example .env
docker compose up
```

### 전체 실행 (앱 포함)
JDK 설치 없이 Docker만으로 실행:
```bash
cp .env.example .env
docker compose --profile full up --build
```

### 접속 정보
| 서비스 | URL |
|--------|-----|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| PostgreSQL | localhost:55432 (builders/builders) |
| Redis | localhost:6379 |

### 데이터 초기화
```bash
docker compose down -v
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
