# ToDoListForAfterMvp

MVP 릴리즈 이후로 미뤄둔 개선·기술부채·운영 과제를 누적하는 문서입니다.
코드 리뷰나 논의 중에 "지금은 MVP 범위 밖이지만 나중에 봐야 한다"고 판단된 항목을 여기 모아두고, 릴리즈 이후 주기적으로 꺼내 다시 우선순위를 매깁니다.

## 기록 규칙

- 새 항목은 카테고리 아래에 추가합니다. 카테고리가 없으면 새로 만듭니다.
- 각 항목은 **무엇을 / 왜 / 언제 다시 볼 트리거**를 한 덩어리로 적습니다.
- 항목 해소 시 삭제하지 말고 `- [x]`로 체크 + 해소 날짜/커밋/PR 번호를 남겨 히스토리 유지.
- 관련 PR/이슈/파일 경로가 있으면 링크로 같이.

---

## 조회수 / 피드

- [ ] **비로그인 유저 조회수 중복 방지 재검토 (정책 변경 시 only)** (PR #56 연계, 2026-04-14 기록)
  - 현재 서비스 정책: **회원가입·로그인 유저만 게시판 접근 가능** (`SecurityConfig.java:67` 에서 `/api/v1/posts/**`를 `hasAnyRole("USER","THERAPIST","ADMIN")`로 제한). 따라서 비로그인 dedup은 현재 **무의미**하며, `PostViewCountService`에서 null-guard를 의도적으로 넣지 않은 것도 같은 이유.
  - **다시 볼 트리거**: "비로그인 유저에게도 게시판을 공개" 로 정책이 바뀌는 순간. 그 시점에 IP/세션 쿠키 기반 키 도입 필요. 안 하면 모든 익명 방문자가 공용 key를 공유해 첫 1회 후 30분 dedup이 전체에 걸림.
  - 파일: `src/main/java/com/therapyCommunity_Vol1/backend/global/cache/PostViewCountService.java`, `SecurityConfig.java:67`

- [ ] **조회수 dedup TTL을 설정값으로 분리** (PR #56)
  - 현재 `TTL_SECONDS = 1800` 하드코딩. A/B 실험이나 운영 튜닝 여지가 있으므로 `application.yaml`로 이전.
  - **다시 볼 트리거**: 조회수 지표가 실제 KPI로 쓰이기 시작하고, 튜닝 요청이 들어올 때.

- [ ] **`getPostDetail` 트랜잭션 안에서 Redis I/O** (PR #56)
  - 트랜잭션 경계 안에서 외부 시스템 호출 → Redis latency만큼 DB 커넥션 점유. MVP 트래픽에선 무시 가능.
  - **다시 볼 트리거**: 피크 QPS에서 DB 커넥션 풀 부족이 관측될 때. 개선 방향은 `isFirstView` 체크를 트랜잭션 진입 전 파사드/컨트롤러 계층으로 올리기.

- [ ] **피드 인기순 `popularity_score` 쓰기 병목 / 동시성 재검토** (PR #55)
  - **현재 결정 (2026-04-15 PR #55 리뷰 합의)**: 반응/스크랩 토글마다 `SELECT COUNT(*) × 2 + UPDATE`로 **정확성 우선**. 델타 업데이트(`score += 30`)가 I/O에선 유리하지만 사용자 경험상 정확한 점수 표시를 우선하기로 함.
  - **남아있는 우려 — 동시성 덮어쓰기**: 같은 글에 여러 토글이 동시에 들어오면 `UPDATE ... = (SELECT COUNT...)` 실행 시점 간 race가 발생해 중간 반영값이 덮어쓰일 수 있음. COUNT 기반이라 최종 일관성은 유지되지만, 토글 직후 짧은 구간에 반영 지연 체감 가능.
  - **대안 방향**: (a) 델타 업데이트로 전환 (b) 행 락 혹은 낙관적 락 도입 (c) 주기적 배치 재계산으로 드리프트 수정
  - **다시 볼 트리거**: ① 인기 글 토글 QPS가 초당 수 회 이상으로 증가 ② DB 쓰기 비용이 모니터링상 눈에 띄게 상승 ③ 사용자/운영 리포트에서 "점수 반영 지연" 이슈 발생

---

## 데이터베이스 / 마이그레이션

- [ ] **V25 마이그레이션 운영 영향 기록** (PR #55)
  - `ALTER TABLE ... UPDATE ... CREATE INDEX` 실행 시 전체 row 락 발생. 현재 데이터 규모에선 문제없지만, `docs/ops/BRANCH_OPERATIONS_GUIDE.md`나 deploy 핸드오프에 "V25 적용 시 짧은 쓰기 락" 언급 필요.
  - **다시 볼 트리거**: 다음 cloud handoff 패키지 업데이트 시 또는 테이블 row 수가 100k를 넘을 때.

---

## 테스트 커버리지

- [ ] **반응/스크랩 토글 시 `recalculatePopularityScore` verify 테스트** (PR #55)
  - `PostReactionServiceTest`, `ScrapServiceTest`에 mocking은 추가됐지만 `verify(postService).recalculatePopularityScore(postId)` 단언이 없음.
  - **다시 볼 트리거**: 해당 서비스에 손대는 다음 PR에서 같이 보강.

---

## 파일 스토리지 / 권한

- [ ] **PostAttachment 유료화 대비 권한 모델 확장** (2026-04-15 기록)
  - 현재 `PostAttachmentService.downloadAttachment`는 `visibilityPolicy.checkAccess()`만 체크 → 공개글이면 로그인한 모두가 다운로드 가능. 결제/구매 개념 없음.
  - 판매자료(유료 첨부) 기능이 붙으면 아래가 필요:
    - Purchase/Order 도메인 (구매 이력 + 결제 완료 상태)
    - `downloadAttachment`에 구매 검증(`purchaseChecker.hasValidPurchase(userId, postId)`) 추가
    - 작성자 본인은 무료, 관리자 예외 처리
    - 단기 토큰(signed URL-style) 또는 presigned S3 URL 발급 — 링크 공유 차단 목적
    - 다운로드 이력 ↔ 결제 이력 연결
  - **다시 볼 트리거**: 판매자료 기능 기획이 구체화될 때.
  - **지금 조심할 것**: 프로필 이미지 옵션 2 작업 중 PostAttachment 엔드포인트 구조는 그대로 유지해야 함(서버 경유 다운로드 = 권한 주입 지점). 공개로 바꾸지 말 것.

- [ ] **파일 리소스 접근 정책 분리 원칙** (2026-04-15 기록)
  - 프로필 이미지 = 공개 리소스(추측 불가한 UUID 파일명, SecurityConfig permitAll), PostAttachment = 권한·결제 검사 필수.
  - 같은 `FileStorageService` 인터페이스를 쓰더라도 **응답 조립/URL 노출 규칙은 리소스 타입별로 별개**.
  - 현재는 문서화만 안 된 상태로 암묵적으로 분리되어 있음 → 새 리소스 타입 추가할 때 실수 가능.
  - **다시 볼 트리거**: 새 파일 리소스 타입(예: 채팅 첨부, 인증서) 추가 논의 시 이 원칙 먼저 적용 여부 확인.

- [ ] **S3 프록시 → CDN/presigned URL 직결 전환** (2026-04-15 기록)
  - 현재 `S3FileStorageService`는 업로드만 S3에 하고 **다운로드는 백엔드가 S3에서 바이트를 읽어와 프록시 스트리밍**. S3의 CDN/직접 링크 장점 하나도 못 씀. 백엔드가 대용량 트래픽의 중간에 껴서 부담.
  - 전환 시 고려할 점:
    - 프로필 이미지: CloudFront + 공개 버킷(또는 OAC)으로 직결
    - PostAttachment: presigned URL 발급(권한 검사 후 단기 URL 생성) → 프록시보다 가볍지만 토큰 만료/재발급 로직 필요
    - 기존 `GET /api/v1/me/profile-image/**` URL은 한동안 유지 → 프론트 전환 기간 확보
  - **다시 볼 트리거**: 월간 S3 GetObject 비용 또는 백엔드 egress 대역폭이 피부에 닿을 때. 또는 이미지 로딩 지연 체감 시.

- [ ] **프로필 이미지 구형 경로 매핑 제거** (2026-04-15 기록, PR #58)
  - `UserController.getProfileImageLegacy` — `/api/v1/me/profile-image/profile-images/{filename}` 매핑을 임시로 유지 중. 배포 직후 Redis `CachedUser`에 남아있는 구형 풀 URL과 브라우저 캐시/DOM에 박힌 구형 URL을 404 없이 처리하기 위함.
  - **제거 조건 (AND)**:
    1. `CachedUser` TTL이 전체 자연 만료(최소 1 사이클 경과)
    2. 운영 관측에서 `/profile-image/profile-images/...` 경로 요청이 일정 기간(1~2 릴리즈) 동안 관측되지 않음
    3. 프론트 빌드 캐시도 뒤집혀 더 이상 구형 URL이 번들에 없음이 확인됨
  - **제거 시 작업**: 해당 메서드 + 이 ToDo 항목 삭제 + 관련 테스트 정리.
  - **다시 볼 트리거**: 다음 메이저 릴리즈 직전 정기 점검, 또는 구형 경로 요청이 모니터링에서 0으로 떨어진 시점.

- [ ] **프로필 이미지 "삭제/기본값 복귀" 기능** (2026-04-15 기록)
  - 현재 `POST /api/v1/me/profile-image` 로 업로드만 가능. 사용자가 프로필 이미지를 없애고 기본값으로 돌리는 경로가 없음. `User.updateProfile`도 null 전달 시 no-op이라 MVP 이전부터 불가능했음.
  - 필요해지면 `DELETE /api/v1/me/profile-image` 엔드포인트 추가 + 기존 S3 객체 정리 로직(optional) 고려.
  - **다시 볼 트리거**: 사용자 피드백이나 기획에서 이 니즈가 올라올 때.

---

## 히스토리 (해소 완료)

- [x] 2026-04-15 — PR #55 — **`popularityScore` 응답 노출 제거**: `TherapyPostSummaryResponse`에서 필드 삭제됨 (원저자가 리뷰 반영).
- [x] 2026-04-15 — PR #55 — **`8640` 매직넘버 주석 추가**: `TherapyPost` 생성자에서 해당 상수의 의미 주석 추가됨 (원저자가 리뷰 반영).
