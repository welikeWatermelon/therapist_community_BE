# Admin Domain

- Path: `src/main/java/com/therapyCommunity_Vol1/backend/admin`

## 책임

- 치료사 인증 심사와 임베딩 백필 같은 운영성 endpoint를 제공한다.

## 진입점

- `AdminTherapistVerificationController`
- `AdminEmbeddingController`
- `AdminTherapistVerificationService`

## 주요 모델

- 자체 aggregate는 거의 없고 `therapist` 와 `post` 도메인을 orchestration 한다.

## 연동

- 인증 심사는 `TherapistVerificationService` 로 위임한다.
- 임베딩 백필은 `post.service.search.EmbeddingBackfillService` 를 사용한다.

## 변경 체크

- admin endpoint는 항상 `SecurityConfig` 의 admin rule에 걸려 있어야 한다.
- 운영성 endpoint는 feature flag 조건과 batch size 상한을 같이 봐야 한다.
