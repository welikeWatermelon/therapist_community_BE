# Therapist Domain

- Path: `src/main/java/com/therapyCommunity_Vol1/backend/therapist`

## 책임

- 치료사 인증 신청, 재신청, 상태 조회, 면허 이미지 조회를 맡는다.

## 진입점

- `TherapistVerificationController`
- `TherapistVerificationService`

## 주요 모델

- `TherapistVerification`
- `TherapistVerificationStatus`

## 연동

- `user` 도메인의 role 승격/강등과 캐시 무효화를 일으킨다.
- `file` 도메인으로 면허 이미지를 저장하고 교체한다.
- `admin` 도메인이 review 흐름을 호출한다.

## 변경 체크

- 신청 실패 시 새 파일 orphan cleanup이 있어야 한다.
- 재신청 시 이전 파일 삭제는 commit 후에만 실행해야 한다.
- 승인/거절 상태 전이와 role 변경이 항상 같이 움직이는지 본다.
