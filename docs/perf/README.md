# 성능 테스트 도구 모음

로컬 환경에서 k6 부하 테스트 + PostgreSQL 쿼리 튜닝을 진행하기 위한 자료.

## 폴더 구조

```
docs/perf/
├── README.md                  ← 이 문서
├── seed/                      ← seed data 생성 가이드
└── k6/                        ← k6 시나리오 스크립트

src/main/resources/db/perf/
└── seed_perf_data.sql         ← 대용량 seed 스크립트 (TRUNCATE 포함)
```

## 1. 대용량 Seed Data 주입

### ⚠️ 주의
`seed_perf_data.sql`은 **도메인 테이블 전체를 TRUNCATE**합니다.
**로컬 DB에서만 실행하세요.** 원격 dev/prod DB에 절대 실행 금지.

### 실행 (docker-compose 기반 로컬 DB 전제)

```bash
# 백엔드 + DB 기동
docker compose up -d db

# seed 주입
docker exec -i builders-db \
    psql -U builders -d builders \
    < src/main/resources/db/perf/seed_perf_data.sql
```

로컬 호스트에서 직접 psql로 접속할 때:

```bash
psql -h localhost -p 55432 -U builders -d builders \
    -f src/main/resources/db/perf/seed_perf_data.sql
```

### 생성 규모

| 테이블 | rows |
|--------|------|
| users | 1,000 |
| therapy_posts | 10,000 |
| therapy_post_comments | ~100,000 |
| therapy_post_reactions | ~500,000 |
| therapy_post_scraps | ~50,000 |

### 분포

- **users**: USER 70% / THERAPIST 28% / ADMIN 2% — role별 접근 제어 테스트 포함 가능
- **posts**: PUBLIC 85% / PRIVATE 15% — visibility 필터 경로 측정 가능
- **reactions**: LIKE 60% / CURIOUS 25% / USEFUL 15% — `countByPostIdInAndReactionType(LIKE)` 현실성 확보
- **comments**: 루트 80k + 대댓글 20k — 스레드형 구조 재현

### 재현성
스크립트 시작에 `SELECT setseed(0.42)`를 고정으로 두어, 같은 환경에서 실행하면
같은 데이터 분포가 나옵니다 (벤치마크 일관성 확보).

### 실행 후 확인

마지막에 각 테이블 row 수를 출력합니다:
```
     table_name        | rows
-----------------------+--------
 users                 |   1000
 therapy_posts         |  10000
 therapy_post_comments | 100000
 therapy_post_reactions|  ~500k
 therapy_post_scraps   |   ~50k
```

ON CONFLICT DO NOTHING으로 reactions / scraps는 약간 줄어드는 게 정상입니다.

## 2. k6 시나리오

`docs/perf/k6/` 아래 4종:
- `feed.js` — 무한스크롤 피드 (커서 페이지네이션)
- `search.js` — 키워드/필터 검색
- `detail.js` — 게시글 상세 조회 (reaction count 포함)
- `reaction.js` — 반응 토글 (쓰기 부하)

상세 실행법은 `docs/perf/k6/README.md` 참조.

## 3. 관찰성 세팅

`application-local.yaml`에 다음 적용:
- Hibernate SQL 로그 (`org.hibernate.SQL=DEBUG`)
- PostgreSQL `auto_explain` 확장 (slow query plan 자동 로깅)
- Spring Actuator / Micrometer metrics

자세한 내용은 `observability.md` 참조 (작성 예정).

## 4. 흐름

```
1) seed data 주입         → 본 문서 §1
2) 관찰성 세팅 활성화      → §3
3) k6 baseline 측정        → §2
4) slow query TOP 10 식별
5) EXPLAIN ANALYZE + 개선 (N+1, 인덱스)
6) k6 재측정 → 개선 수치 비교
7) 성능 리포트 작성 (REPORT.md)
```

## 리포트 작성 위치

- `docs/perf/REPORT.md` — 최종 벤치마크 개선 전/후 기록
