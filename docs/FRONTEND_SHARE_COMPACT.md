# Frontend Share (Compact)

## Endpoints
- Base URL: `43.203.40.3`
- Swagger: `http://43.203.40.3:8080/swagger-ui/index.html#/`
- OpenAPI: `http://43.204.40.3:8080/v3/api-docs`

## Auth
- Header: `Authorization: Bearer <accessToken>`
- Login API: `POST /api/v1/auth/login`
- 401: 인증 실패/토큰 문제
- 403: 권한 부족

## Test Accounts (Staging)
- ADMIN: `admin@test.com` / `<PASSWORD>`
- USER: `testUser@test.com` / `<PASSWORD>`
- THERAPIST: `testTherapist@test.com` / `<PASSWORD>`

## Important API Notes
- `POST /api/v1/therapist-verifications`
  - `multipart/form-data`
  - fields: `licenseCode`(string), `licenseImage`(file)
- `GET /api/v1/therapist-verifications/me/image`
  - JSON(`ApiResponse`) 아님
  - binary file response (`Content-Type`, `Content-Disposition`)

## Front CORS
- Allowed origins (current): `http://localhost:3000`, `http://127.0.0.1:3000`, `http://localhost:5173`,`http://127.0.0.1:5173`

## Quick Check
1. `USER` 로그인 -> 치료사 신청
2. `THERAPIST` 로그인 -> posts/comments/scrap API 접근
3. `ADMIN` 로그인 -> `/api/v1/admin/**` 접근
