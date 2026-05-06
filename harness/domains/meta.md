# Meta Domain

- Path: `src/main/java/com/therapyCommunity_Vol1/backend/meta`

## 책임

- 공개 홈/헬스 응답과 약관 URL endpoint를 맡는다.

## 진입점

- `HomeController`
- `TermsController`
- `TermsService`

## 주요 모델

- 별도 aggregate는 없고 공개 메타 응답만 제공한다.

## 연동

- 약관 URL은 `file` 저장소의 presigned URL 경로에 의존한다.

## 변경 체크

- 이 경로들은 public endpoint이므로 `SecurityConfig` 와 함께 봐야 한다.
- 약관 URL TTL을 바꾸면 프론트 사용 패턴도 같이 확인한다.
