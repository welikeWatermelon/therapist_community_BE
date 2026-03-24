# Cloud Handoff Package

클라우드 팀에 전달할 기준 문서입니다. 이 문서 하나와 저장소 링크를 같이 전달하면 됩니다.

## 1) 전달 방법

클라우드 팀에는 아래 3가지만 주면 됩니다.

1. 저장소 링크와 배포 대상 브랜치 규칙
2. 이 문서 링크
3. 시크릿 값 별도 전달 채널

권장 전달 문구:

```text
[Backend -> Cloud Handoff]
- Repo: <REPO_URL> https://github.com/AIRO-offical/therapist_community_BE.git
- Branch for deploy: main
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
JWT_REFRESH_SECRET=<LONG_RANDOM_SECRET>
JWT_ACCESS_TTL_SEC=1800
JWT_REFRESH_TTL_SEC=1209600

APP_AWS_REGION=ap-northeast-2
APP_AWS_S3_BUCKET=<S3_BUCKET_NAME>
```

주의:

- `JWT_ACCESS_SECRET`, `JWT_ACCESS_TTL_SEC`, `JWT_REFRESH_TTL_SEC` 는 현재 코드에서 직접 사용합니다.
- `JWT_REFRESH_SECRET` 은 운영 설정셋에는 포함하는 편이 맞지만, 현재 코드에서는 아직 직접 참조하지 않습니다.

CORS 관련:

- 운영 허용 origin 요구사항: `https://app.melonnetherapists.com`, `https://www.melonnetherapists.com`
- 운영 프로필 기준 설정 위치: [application-prod.yaml](/Users/tom/dev/buildersMvp/backend/src/main/resources/application-prod.yaml#L7)
- 로컬 기본값 설정 위치: [application.yaml](/Users/tom/dev/buildersMvp/backend/src/main/resources/application.yaml#L13)

현재 허용 origin:

- 로컬 기본값: `http://localhost:3000`, `http://127.0.0.1:3000`, `http://localhost:5173`, `http://127.0.0.1:5173`
- 운영(prod) 값: `https://app.melonnetherapists.com`, `https://www.melonnetherapists.com`

## 5) Nginx 기준

클라우드 팀 작업 범위:

- `api.melonnetherapists.com` 에 대한 TLS 종료
- `443` 요청을 `127.0.0.1:8080` 으로 프록시
- `X-Forwarded-*` 헤더 전달
- 업로드 고려 시 `client_max_body_size` 설정

현재 코드 기준 업로드 제한:

- `5MB`

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

- [TherapistVerificationService.java](/Users/tom/dev/buildersMvp/backend/src/main/java/com/therapyCommunity_Vol1/backend/therapist/service/TherapistVerificationService.java)

클라우드 팀 작업:

1. CloudWatch Agent 또는 journald 수집 구성
2. 위 2개 문자열에 대한 metric filter 생성
3. SNS 이메일 알람 연결
4. `Sum >= 1` 기준으로 알람 설정

## 8) 브랜치 전략

MVP 권장 규칙:

- 배포 브랜치: `main`
- 기능 개발: `feature/*`
- 긴급 수정: `hotfix/*`
- `main` 직접 푸시 금지
- PR 리뷰 1명 필수
- Squash merge 후 `main`
- `main` 보호 규칙: CI 통과 필수

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

## 12) 현재 코드 기준 메모

- 헬스체크는 현재 `/api/v1/home` 가 실제 구현 엔드포인트입니다.
- CORS origin은 현재 환경변수화되어 있지 않습니다.
- Swagger/OpenAPI 는 기본 제공 경로가 열려 있습니다.
- systemd 운영은 이미 [SYSTEMD_DEPLOYMENT.md](/Users/tom/dev/buildersMvp/backend/docs/SYSTEMD_DEPLOYMENT.md) 에 더 자세한 절차가 있습니다.
