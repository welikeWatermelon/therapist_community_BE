<div align="center">

# MelonMe

### 치료사 전용 커뮤니티 플랫폼 백엔드

![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-JWT-6DB33F?logo=springsecurity&logoColor=white)
![AWS S3](https://img.shields.io/badge/AWS_S3-232F3E?logo=amazons3&logoColor=white)

</div>

---

## 서버 구조도

<!-- 📸 사진 필요: 전체 아키텍처 다이어그램 이미지 -->
<!-- draw.io / excalidraw 등으로 아래 구조를 그려서 이미지로 삽입 -->
<!-- 포함 요소: Client → Spring Boot (JwtAuthFilter → Controller → Service → Repository) → PostgreSQL / Redis / S3 / SSE -->

```
[현재 구조 요약]

Client
  └─ REST + JWT Bearer ──▶ Spring Boot 3.5
                              ├─ JwtAuthFilter (STATELESS)
                              ├─ 13개 도메인 모듈
                              │    auth / user / post / comment / reaction
                              │    scrap / notification / therapist / admin
                              │    application / file / meta / global
                              ├─ @TransactionalEventListener + @Async
                              │    └─ SSE 실시간 알림 ──▶ Client
                              └─ PostgreSQL 16 / Redis 7 / AWS S3
```

---

## 프로젝트 목표

- 치료사 면허 인증을 통해 검증된 전문가만 참여하는 커뮤니티 플랫폼을 구현하는 것이 목표입니다.
- 단순한 기능 구현뿐 아니라 JWT 보안, Redis 캐싱, SSE 실시간 알림, 관련도 검색 등 실무 수준의 기술적 과제를 직접 해결하는 것이 목표입니다.
- DDD 경계 원칙, 3계층 테스트 분리, Flyway 마이그레이션 등 유지보수 가능한 코드베이스를 만드는 것이 목표입니다.

---

## 기술적 이슈 해결 과정

- **[#8] SSE 실시간 알림에서 다중 탭 지원과 유실 이벤트 복구 구현**
  `ConcurrentHashMap<userId, ConcurrentHashMap<emitterId, SseEmitter>>` 중첩 구조로 사용자당 N개의 탭을 동시에 지원하고, `Last-Event-ID` 헤더 기반으로 재연결 시 누락된 이벤트를 자동 재전송했습니다.
  [상세 보기 →](https://www.notion.so/02-2-SSE-35f2eed33827809e90f5c52c7d72e2b1)

- **[#7] 트랜잭션 커밋 이후에만 알림을 전송해야 하는 문제**
  `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` 전용 스레드풀(core 2, max 4, CallerRunsPolicy)로 트랜잭션 롤백 시 알림 미발송을 보장하면서 요청 스레드를 블로킹하지 않도록 했습니다.
  [상세 보기 →](https://www.notion.so/02-2-SSE-35f2eed33827809e90f5c52c7d72e2b1)

- **[#6] JWT Refresh Token 탈취 시 세션 전체를 무효화하는 문제**
  `tokenFamily` UUID로 같은 기기/세션의 토큰을 그룹화하고, 이미 폐기된 토큰으로 갱신 요청이 들어오면 해당 family 전체를 `REUSE_DETECTED`로 일괄 폐기했습니다. DB에는 원본 토큰 대신 SHA-256 해시만 저장합니다.
  [상세 보기 →](https://www.notion.so/09-JWT-Refresh-Token-Family-Rotation)

- **[#5] Elasticsearch 없이 관련도 기반 검색을 구현하는 문제**
  PostgreSQL `pg_trgm` 확장 + GIN 인덱스만으로 `similarity()` 점수 기반 검색을 구현했습니다. `SET LOCAL`로 threshold를 트랜잭션 스코프 내에서만 적용해 전역 오염을 방지하고, native SQL 스코어 정렬 순서가 JPA JOIN 시 깨지는 문제를 2단계 fetch(ID+score → author 포함 재조회)로 해결했습니다.
  [상세 보기 →](https://www.notion.so/12-GIN-Trigram-pgvector-Strategy-35f2eed33827815dace0d0941786f1d6)

- **[#4] 커서 페이지네이션에서 부동소수점 동등비교 오차 문제**
  인기도 점수(`reactions * 3/10 + scraps * 2/10 + epoch / 86400`)의 소수를 그대로 쓰면 커서 비교 시 오차가 발생합니다. 공식의 분자를 10배 스케일하여 Long 타입(`reactions * 30 + scraps * 20 + epoch / 8640`)으로 정수화해 완전한 동등비교를 보장했습니다.

- **[#3] Redis 장애 시 핵심 기능이 차단되는 문제**
  모든 Redis 연동을 `try-catch`로 감싸 Redis 다운 시 DB 직접 조회 또는 기능 허용으로 graceful degradation 했습니다. 유저 캐시는 TTL jitter(1800 + rand(300)초)로 cache avalanche를, null 캐싱(60초)으로 cache penetration을 방지합니다.
  [상세 보기 →](https://www.notion.so/10-Spring-Security-Redis)

- **[#2] 도메인 간 데이터 접근 시 결합도가 높아지는 문제**
  다른 도메인의 Repository를 직접 주입하지 않는 **DDD 경계 원칙**을 프로젝트 전체에 적용했습니다. 예) `ScrapService → PostService.recalculatePopularityScore()` (NG: `ScrapService → TherapyPostRepository` 직접 접근).

- **[#1] Stateless 서버에서 조회수 중복 방지 구현**
  세션/쿠키 없이 Redis `SETNX(post_view:{postId}:{userId}, TTL 30분)`로 사용자당 30분 이내 중복 조회를 원자적으로 차단했습니다. `Boolean? wasAbsent` null 방어 처리로 Redis 장애 시 NPE를 방지합니다.

---

## 프로젝트 중점사항

- JWT Refresh Token Family Rotation — 탈취 감지 시 family 전체 자동 폐기
- SSE 실시간 알림 — 다중 탭 동시 지원, Last-Event-ID 유실 이벤트 복구
- PostgreSQL pg_trgm GIN 인덱스 — 추가 인프라 없이 관련도 기반 검색
- Redis 3가지 패턴 — 조회수 SETNX / 로그인 Rate Limit / 유저 캐시 Cache-Aside
- DDD 도메인 경계 — 크로스 도메인 접근은 반드시 상대 도메인 Service 경유
- 3계층 테스트 분리 (63개) — @SpringBootTest 통합 / standaloneSetup 슬라이스 / 단위
- Flyway V1-V24 마이그레이션 — DB 변경 이력 코드로 관리
- 파일 저장소 추상화 — S3(prod) / Local(dev) 환경별 자동 전환
- @TransactionalEventListener(AFTER_COMMIT) + @Async — 트랜잭션 안전 비동기 알림
- 치료사 면허 인증 워크플로 — PENDING → APPROVED/REJECTED → 재신청

---

## ERD

<!-- 📸 사진 필요: ERD 이미지 -->
<!-- DBeaver, pgAdmin, dbdiagram.io 등으로 ER 다이어그램 캡처 후 삽입 -->
<!-- 포함 테이블: users, refresh_tokens, therapy_posts, post_images, post_attachments, -->
<!--             therapy_post_comments, post_reactions, comment_reactions, scraps, -->
<!--             post_download_histories, notifications, therapist_verifications -->

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Framework | Spring Boot 3.5, Spring Security, Spring Data JPA, Spring Data Redis |
| Language | Java 17 |
| Database | PostgreSQL 16 + pg_trgm (GIN), H2 (테스트) |
| Cache | Redis 7 |
| Auth | JWT HS256 — Access 30분 / Refresh 14일 (HttpOnly Cookie, Family Rotation) |
| Migration | Flyway V1–V24 |
| File Storage | AWS S3 (prod) / Local FileSystem (dev) |
| API Docs | springdoc-openapi 2.8.5 (Swagger UI) |
| Build | Gradle |

---

## Quick Start

```bash
# 인프라 실행 (PostgreSQL + Redis)
cp .env.example .env
docker compose up -d

# 애플리케이션 실행
./gradlew bootRun --args='--spring.profiles.active=local'

# 빌드 & 테스트
./gradlew clean build
./gradlew test
```

| 서비스 | URL |
|--------|-----|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| PostgreSQL | localhost:55432 |
| Redis | localhost:6379 |

---

## 관련 문서

| 분류 | 링크 |
|------|------|
| 아키텍처 | [ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) |
| 설계 결정 | [DECISIONS.md](docs/architecture/DECISIONS.md) |
| API 명세 | [API_SPEC.md](docs/api/API_SPEC.md) |
| 포트폴리오 (Notion) | [기술 심화 정리](https://www.notion.so/35f2eed33827815dace0d0941786f1d6) |
