# Backend Collaboration (Minimal)

## 1) Repository
- GitHub: `https://github.com/ttttom1/melonneMvpMvp.git`
- Default branch: `main`

## 2) Config Files (.env vs application.yaml)
- `application.yaml`: 공통 기본 설정
- `application-local.yaml`: 로컬 실행 설정 (`bootRun --spring.profiles.active=local` 시 사용)
- `.env`: 현재는 Spring이 직접 읽지 않음, `docker-compose` 변수 치환용
- EC2 운영환경 변수: `/etc/backend/backend.env` 사용 (`systemd`에서 로드)

## 3) Local Run
- Start: `./gradlew bootRun --args='--spring.profiles.active=local'`
- Test: `./gradlew test`

## 4) API Docs (Staging)
- Base URL: `http://43.203.40.3:8080`
- Swagger: `http://43.203.40.3:8080/swagger-ui/index.html#/`
- OpenAPI JSON: `http://43.203.40.3:8080/v3/api-docs`

## 5) Test Accounts (Staging)
- ADMIN: `admin@test.com` / `<PASSWORD_SEPARATE_SHARE>`
- USER: `testUser@test.com` / `<PASSWORD_SEPARATE_SHARE>`
- THERAPIST: `testTherapist@test.com` / `<PASSWORD_SEPARATE_SHARE>`

## 6) Auth Rules
- Header: `Authorization: Bearer <accessToken>`
- 401: 인증 안 됨/토큰 오류
- 403: 인증은 됐지만 권한 부족

## 7) Ubuntu Deploy / Restart
- EC2 접속:
```bash
ssh -i /Users/tom/dev/buildersMvp/melonne-key.pem ubuntu@43.203.40.3
```
- 서비스 재시작:
```bash
sudo systemctl restart backend
```
- 상태/로그 확인:
```bash
sudo systemctl status backend
journalctl -u backend -n 200 --no-pager
```

## 8) RDS Console Quick Check
- AWS Console -> RDS -> Databases -> `melonne-db`
- `Connectivity & security`에서 확인:
  - Endpoint
  - Port (`5432`)
  - VPC / Security Group 연결 상태
- `Configuration`에서 확인:
  - DB engine/version
  - DB instance status = `Available`

## 9) Share Separately (Do Not Commit)
- 실제 DB/JWT/S3 시크릿 값
- 테스트 계정 비밀번호
- AWS IAM 자격증명

## 10) Team Rule
- API 스펙 변경 시 Swagger 어노테이션 + PR 설명에 변경점 명시
