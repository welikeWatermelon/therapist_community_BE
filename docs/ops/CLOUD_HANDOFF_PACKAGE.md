# Cloud Handoff Package

클라우드/배포 관련 단일 기준 문서입니다.
EC2, Nginx, systemd, 환경변수, CORS, S3, CloudWatch, 체크리스트는 이 문서만 기준으로 봅니다.
기존에 흩어져 있던 systemd, cloud setup, CloudWatch 운영 내용은 이 문서로 통합했습니다.
서버 진단 절차는 [SERVER_CHECK_RUNBOOK.md](./SERVER_CHECK_RUNBOOK.md)에서 따로 관리합니다.

## 1) 전달 방법

클라우드 팀에는 아래 3가지만 주면 됩니다.

1. 저장소 링크와 배포 대상 브랜치 규칙 
2. 이 문서 링크
3. 시크릿 값 별도 전달 채널

권장 전달 문구:

```text
[Backend -> Cloud Handoff]
- Repo: <REPO_URL>
- Branch for deploy: deploy
- Branch guide: docs/BRANCH_OPERATIONS_GUIDE.md
- Handoff doc: docs/CLOUD_HANDOFF_PACKAGE.md
- Secrets: 1Password/DM로 별도 전달

이번 범위:
- EC2 + Nginx + systemd 배포
- 도메인: api.melonnetherapists.com
- 수동 승인 포함 GitHub Actions 초안
- CloudWatch 로그 수집 + 2개 알람 설정
```

## 2) 배포 대상

- 인프라: EC2 + Nginx + systemd
- 도메인: `api.melonnetherapists.com`
- 애플리케이션 포트: `8080`
- 외부 진입: `443 -> Nginx -> 127.0.0.1:8080`

현재 백엔드 기준 공개 확인 URL:

- Swagger UI: `https://api.melonnetherapists.com/swagger-ui/index.html`
- OpenAPI JSON: `https://api.melonnetherapists.com/v3/api-docs`
- 헬스체크: `https://api.melonnetherapists.com/api/v1/home`

주의:
- `/api/v1/health` 는 보안 설정에는 열려 있지만 현재 코드에는 엔드포인트 구현이 없습니다.
- 운영 헬스체크는 당장은 `/api/v1/home` 기준으로 잡는 것이 맞습니다.

## 3) 빌드/실행 기준

로컬 또는 CI 빌드:

```bash
./gradlew clean bootJar
```

실행:

```bash
java -jar build/libs/backend-0.0.1-SNAPSHOT.jar
```

`backend.service` 예시:

```ini
[Unit]
Description=Melonne Therapist Backend
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/backend/current
EnvironmentFile=/etc/backend/backend.env
ExecStart=/usr/bin/java -jar /opt/backend/current/backend.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

배포 후 확인:

```bash
sudo systemctl daemon-reload
sudo systemctl enable backend
sudo systemctl restart backend
sudo systemctl status backend
journalctl -u backend -n 200 --no-pager
```

## 4) 환경변수 목록

현재 코드 기준 필수/운영 권장 값입니다.

```bash
SPRING_PROFILES_ACTIVE=prod

SPRING_DATASOURCE_URL=jdbc:postgresql://<RDS_ENDPOINT>:5432/<DB_NAME>
SPRING_DATASOURCE_USERNAME=<DB_USER>
SPRING_DATASOURCE_PASSWORD=<DB_PASSWORD>

JWT_ACCESS_SECRET=<LONG_RANDOM_SECRET>
JWT_ACCESS_TTL_SEC=1800
JWT_REFRESH_TTL_SEC=1209600

APP_AWS_REGION=ap-northeast-2
APP_AWS_S3_BUCKET=<S3_BUCKET_NAME>
APP_CORS_ALLOWED_ORIGINS=https://www.melonnetherapists.com

# === 검색 (현재 불필요, pgvector 전환 시에만 추가) ===
# OPENAI_API_KEY=sk-xxxx
# APP_SEARCH_STRATEGY=pgvector
# APP_SEARCH_EMBEDDING_ENABLED=true
```

주의:

- `JWT_ACCESS_SECRET`, `JWT_ACCESS_TTL_SEC`, `JWT_REFRESH_TTL_SEC` 는 현재 코드에서 직접 사용합니다.
- `APP_CORS_ALLOWED_ORIGINS` 는 서버 프로필 YAML에서 필수입니다.

CORS 관련:
- 운영/개발 서버의 허용 origin은 `APP_CORS_ALLOWED_ORIGINS`로 주입합니다.
- 운영 예시: `https://www.melonnetherapists.com`
- 여러 도메인을 허용해야 하면 쉼표로 구분합니다.
- `localhost:3000`, `localhost:5173`은 공통 설정의 로컬 개발 기본값입니다. `prod`에서는 `application-prod.yaml`이 `APP_CORS_ALLOWED_ORIGINS`로 이를 덮어쓰므로 운영 환경에 넣지 않으면 열리지 않습니다.

## 5) Nginx 기준

클라우드 팀 작업 범위:

- `api.melonnetherapists.com` 에 대한 TLS 종료
- `443` 요청을 `127.0.0.1:8080` 으로 프록시
- `X-Forwarded-*` 헤더 전달
- 업로드 고려 시 `client_max_body_size` 설정
- EC2 Security Group에서 `22`, `80`, `443` 허용
- RDS Security Group에서 EC2 Security Group의 `5432` 접근 허용

현재 코드 기준 업로드 제한:

- `10MB`

## 6) 롤백 기준

롤백 기준:

- 서비스 기동 실패
- 헬스체크 `/api/v1/home` 실패
- 핵심 API 로그인/게시글 조회 실패

롤백 방법:

1. 직전 정상 JAR를 `current/backend.jar` 로 복구
2. `sudo systemctl restart backend`
3. `journalctl -u backend -n 200 --no-pager` 로 기동 확인

예시:

```bash
cp /opt/backend/releases/<PREVIOUS_JAR> /opt/backend/current/backend.jar
sudo systemctl restart backend
```

## 7) CloudWatch 로그/알람 요구

로그 수집:

- source: `journalctl -u backend`
- target log group 예시: `/ec2/backend`

필수 알람 2개:

1. `FILE_DELETE_FAILED`
2. `THERAPIST_APPLY_FAILED_AFTER_UPLOAD`

로그 발생 위치:

- [TherapistVerificationService.java](../src/main/java/com/therapyCommunity_Vol1/backend/therapist/service/TherapistVerificationService.java)

클라우드 팀 작업:

1. CloudWatch Agent 또는 journald 수집 구성을 통해 `/ec2/backend` 같은 Log Group으로 `journalctl -u backend` 를 수집
2. `FILE_DELETE_FAILED` -> `Backend/FileDeleteFailedCount` metric filter 생성
3. `THERAPIST_APPLY_FAILED_AFTER_UPLOAD` -> `Backend/TherapistApplyFailedAfterUploadCount` metric filter 생성
4. SNS Topic 생성 후 이메일 또는 Slack 구독 연결
5. 두 metric 모두 `Sum >= 1`, missing data `not breaching` 기준으로 알람 설정
6. 실패 로그를 1건 발생시켜 metric 증가와 알림 수신을 검증

## 8) 브랜치 전략

현재 저장소는 아래 과도기 전략을 사용합니다.

- 배포 브랜치: `deploy`
- 개발 통합 브랜치: `main`
- 기능 개발: `feat/*`
- 긴급 수정: `hotfix/*`
- `deploy`, `main` 직접 푸시 금지
- 운영 서버는 `deploy`만 배포
- 기능 브랜치는 `main`에서 분기
- hotfix 브랜치는 `deploy`에서 분기

상세 규칙은 [BRANCH_OPERATIONS_GUIDE.md](./BRANCH_OPERATIONS_GUIDE.md) 문서를 기준으로 봅니다.

## 9) CI/CD 분담

백엔드 담당:

- 코드
- 테스트
- API 계약
- OpenAPI/Swagger 유지

클라우드 담당:

- GitHub Actions
- 배포 스크립트
- IAM
- SSL/TLS
- Nginx
- CloudWatch 로그/알람

배포 정책:

- 초기에는 manual approval 포함
- 완전 자동 배포는 운영 안정화 후 도입

## 10) 클라우드 팀 체크리스트

1. EC2에 Java 17 설치
2. `/opt/backend/current`, `/opt/backend/releases`, `/etc/backend` 디렉터리 준비
3. `backend.env` 생성 및 시크릿 주입
4. `backend.service` 등록
5. Nginx reverse proxy 설정
6. SSL 인증서 연결
7. `/api/v1/home` 헬스체크 확인
8. CloudWatch 로그 수집 확인
9. 알람 2개 생성
10. 수동 승인 포함 GitHub Actions 배포 플로우 구성

## 11) 별도 전달해야 하는 것

문서에 넣지 말고 별도 채널로 전달:

- 실제 DB 계정/비밀번호
- JWT secret
- AWS IAM 자격증명 또는 IAM Role 정책 정보
- 테스트 계정 비밀번호

EC2 IAM Role 최소 권한 예시:

- `s3:GetObject`
- `s3:PutObject`
- `s3:DeleteObject`
- 필요 시 `s3:ListBucket`

## 12) 검색 엔진 설정 (pgvector 전환 가이드)

현재 검색은 **GIN trigram (pg_trgm)** 기반이며, 추가 설정 없이 동작합니다.
향후 검색 정확도가 부족하면 **pgvector (OpenAI 임베딩)** 로 전환합니다.

### 현재 상태 (Phase 1 — GIN)

추가 환경변수 불필요. 기본값으로 동작합니다.

```yaml
# application.yaml 기본값 (변경 불필요)
app:
  search:
    strategy: gin           # GIN trigram 검색
    embedding:
      enabled: false        # 임베딩 파이프라인 비활성화
```

- `OPENAI_API_KEY` — **지금은 불필요**, `.env`에 비워두면 됨
- Docker 이미지가 `pgvector/pgvector:pg16`이지만, GIN 모드에서는 pgvector 기능을 사용하지 않음

### 전환 시 (Phase 2 — pgvector)

백엔드 팀이 전환을 결정하면 클라우드 팀에 아래를 요청합니다.

**1) 환경변수 추가** (`backend.env`):

```bash
# 검색 전환용 — 백엔드 팀 요청 시에만 추가
OPENAI_API_KEY=sk-xxxx                     # OpenAI API 키 (시크릿 채널로 전달)
APP_SEARCH_STRATEGY=pgvector               # 검색 전략 전환
APP_SEARCH_EMBEDDING_ENABLED=true          # 임베딩 생성 활성화
```

**2) 전환 전 사전 조건** (백엔드 팀이 확인):

```sql
-- 모든 게시글에 임베딩이 생성되어 있어야 함
SELECT COUNT(*) FROM therapy_posts
WHERE content_embedding IS NULL AND deleted_at IS NULL;
-- 결과가 0이어야 전환 가능
```

**3) 전환 절차**:

| 순서 | 담당 | 작업 |
|------|------|------|
| 1 | 백엔드 | GIN 모드에서 `embedding.enabled=true`로 임베딩 사전 생성 |
| 2 | 백엔드 | admin API로 기존 게시글 backfill 완료 확인 |
| 3 | 백엔드 → 클라우드 | 환경변수 3개 전달 |
| 4 | 클라우드 | `backend.env`에 추가 후 서비스 재시작 |
| 5 | 백엔드 | 검색 품질 모니터링 |

**4) 롤백** (검색 품질 문제 시):

```bash
# backend.env에서 아래만 변경
APP_SEARCH_STRATEGY=gin
# 재시작
sudo systemctl restart backend
```

`embedding.enabled`는 켜둬도 무방 (백그라운드 임베딩 생성만 계속됨).

### 비용 참고

- 모델: `text-embedding-3-small` ($0.02 / 1M tokens)
- 게시글 1건 ≈ 100~200 tokens → 1,000건 ≈ $0.004
- 검색 쿼리당 1회 API 호출 (캐싱 적용됨)

## 13) 현재 코드 기준 메모

- 헬스체크는 현재 `/api/v1/home` 가 실제 구현 엔드포인트입니다.
- `prod`/`dev`의 CORS origin은 `APP_CORS_ALLOWED_ORIGINS`로 환경변수화되어 있습니다.
- `application.yaml`의 `localhost:3000`, `localhost:5173` 기본값은 로컬 프론트 개발 편의를 위한 값입니다.
- Swagger/OpenAPI 는 기본 제공 경로가 열려 있습니다.
- 서버 점검은 [SERVER_CHECK_RUNBOOK.md](./SERVER_CHECK_RUNBOOK.md) 기준으로 확인합니다.
