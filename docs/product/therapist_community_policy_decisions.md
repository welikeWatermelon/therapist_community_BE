# Therapist Community Policy Decisions

기준일: 2026-03-27

## 전제

- 원본 Figma 링크는 이 환경에서 직접 열람할 수 없어서, 아래 문서는 사용자가 제공한 설명인 `Threads 기반 치료사 전용 커뮤니티`와 현재 백엔드 코드 구조를 기준으로 작성했다.
- 정책의 기본 레퍼런스는 Threads의 공식 제품 방향이다.
  - [Introducing Threads: A New Way to Share With Text](https://about.fb.com/news/2023/07/introducing-threads-new-app-text-sharing/)
  - [New Threads Features for a More Personalized Experience That You Control](https://about.fb.com/news/2025/03/new-threads-features-more-personalized-experience-you-control/)

## 이번 문서에서 먼저 확정한 정책

### 1. 커뮤니티는 공개형이 아니라 폐쇄형이다.

Threads는 공개 대화에 강한 제품이지만, 이 서비스는 `치료사 전용 커뮤니티`라는 점이 더 중요하다. 따라서 가입은 누구나 가능하더라도 커뮤니티 콘텐츠의 조회, 작성, 댓글, 첨부자료 접근은 `승인된 치료사`와 `관리자`로 제한한다.

### 2. 피드는 Threads처럼 가볍게, 권한은 Threads보다 엄격하게 간다.

- 기본 피드는 추천 피드로 둔다.
- 추천 로직은 초기 MVP에서는 완전 개인화 대신 `최신성 + 조회수 + 치료영역 일치도`를 합산한 규칙 기반으로 시작한다.
- 필터는 Threads의 topic 개념을 그대로 쓰기보다 `치료영역`, `연령대`, `게시글 유형`으로 치환한다.

### 3. 게시글은 스레드형이지만 제목은 유지한다.

Threads의 짧은 대화 흐름은 차용하되, 현재 백엔드 데이터 모델과 운영 문서화 편의성을 고려해 MVP 게시글은 `제목 + 본문` 구조로 고정했다. 이 결정은 기획보다 구현 정합성을 우선한 것이다.

### 4. 리포스트와 인용 게시물은 MVP에서 제외한다.

Threads에서는 인용과 재공유가 중요한 참여 방식이지만, 치료 사례는 맥락이 분리되거나 과도하게 확산되면 위험하다. 그래서 `리포스트`, `인용 게시`, `재배포 증폭` 기능은 MVP 범위에서 제외했다.

### 5. DM도 MVP에서 제외한다.

Threads는 1:1 메시지 기능이 추가됐지만, 치료사 커뮤니티에서 비공개 메시지는 사례 공유가 운영 통제 밖으로 이동할 수 있다. 별도의 보안, 보존, 신고 체계 없이 메시징을 넣는 것은 리스크가 커서 제외했다.

### 6. 사례 공유는 반드시 비식별화가 전제다.

이 서비스의 가장 강한 정책은 여기다. Threads 일반 커뮤니티보다 더 엄격하게 아래 항목을 금지한다.

- 환자 실명
- 얼굴이 식별되는 사진
- 연락처, 주민번호, 주소
- 상세 기관명, 보호자 이름, 특정 일정 정보처럼 환자를 재식별할 수 있는 정보

운영자는 이런 콘텐츠를 발견하면 즉시 숨김 또는 삭제할 수 있어야 한다.

### 7. 안전 도구는 Threads 방향을 따르되, 2차 릴리즈로 둔다.

Threads의 `답글 제어`, `팔로워만 답글`, `인용 제한`, `숨김 단어` 같은 안전 제어는 방향성이 맞다. 다만 현재 백엔드 기준으로는 `신고`, `차단`, `숨김 단어`, `답글 공개 승인`은 2차 릴리즈가 적절하다.

## 현재 백엔드와의 정합성 판단

### 이미 있는 축

- 회원가입, 로그인, 토큰 발급
- 치료사 인증 신청/조회
- 관리자 인증 승인/반려
- 게시글, 댓글, 반응, 스크랩
- PDF 첨부와 다운로드 이력
- 기본 필터 검색

### 문서에는 있으나 실제 구현 확인이 필요한 축

- 알림 센터
- SSE 실시간 알림

### 새로 필요한 축

- 커뮤니티 접근 강제 차단
- 팔로우/팔로잉 피드
- 신고/차단/숨김 단어
- 운영자 제재 이력과 감사 로그
- 프로필의 전문분야/주제 정보

## 산출물 구성

- 기능명세서: [therapist_community_feature_spec.csv](/Users/tom/dev/buildersMvp/backend/docs/product/therapist_community_feature_spec.csv)
- 요구사항명세서: [therapist_community_requirements_spec.csv](/Users/tom/dev/buildersMvp/backend/docs/product/therapist_community_requirements_spec.csv)

## 작성 원칙

- `Threads와 유사한 경험`보다 `치료사 전용 안전성`을 우선했다.
- `지금 백엔드가 이미 갖고 있는 구조`와 심하게 충돌하는 기획은 피했다.
- 기능은 `MVP`, `Phase 2`, `Out of Scope`로 나눠서 바로 실행 가능한 문서가 되도록 정리했다.
