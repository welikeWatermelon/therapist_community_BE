# 성능 측정 직접 해보기 (Hands-on)

## 사전 준비 확인

- ✅ Docker Desktop 실행 중 (🐳 메뉴바 아이콘 고정)
- ✅ `brew install k6` 완료
- ✅ IntelliJ 종료 (메모리 여유 확보)
- ✅ 컨테이너 실행 중: `docker ps` 로 `builders-db` + `builders-redis` 확인
- ✅ 백엔드 실행 중: `curl http://localhost:8080/actuator/health` 가 `{"status":"UP"}`

아직 안 돼 있으면 [아래 재기동 절차](#부록-1-환경-재기동) 참조.

---

## Step 1. 볼륨 조정

`src/main/resources/db/perf/seed_perf_data.sql` 파일 상단의 6줄만 바꾸면 규모 조절됨:

```sql
\set N_USERS          1000        ← 유저 수
\set N_POSTS          10000       ← 게시글 수
\set N_COMMENTS_ROOT  80000       ← 루트 댓글
\set N_COMMENTS_REPLY 20000       ← 대댓글
\set N_REACTIONS_TRY  700000      ← 반응 생성 시도 (UNIQUE 충돌로 실제는 줄어듦)
\set N_SCRAPS_TRY     70000       ← 스크랩 생성 시도
```

### 권장 스케일 세트

| 세트 | N_USERS | N_POSTS | N_COMMENTS_ROOT | N_COMMENTS_REPLY | N_REACTIONS_TRY | N_SCRAPS_TRY | 소요 |
|------|---------|---------|------------------|-------------------|------------------|---------------|------|
| S (default, baseline) | 1000 | 10000 | 80000 | 20000 | 700000 | 70000 | ~20s |
| M (×5) | 5000 | 50000 | 400000 | 100000 | 3500000 | 350000 | ~1~2min |
| **L (×10, 권장 다음 테스트)** | **10000** | **100000** | **800000** | **200000** | **7000000** | **700000** | **~3~5min** |

**L 세트로 먼저 가보세요.** prod 규모 근접이고 디스크/메모리 버텨냄 (약 1.5GB DB 볼륨).

### 변경 후 저장 → 실행

```bash
docker exec -i builders-db psql -U builders -d builders \
    < src/main/resources/db/perf/seed_perf_data.sql
```

마지막에 각 테이블 실제 row 수가 출력됩니다.

---

## Step 2. k6 재측정

### 2-1. 토큰이 만료됐으면 재발급

```bash
RESP=$(curl -s -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "k6-tester2@test.local",
    "password": "password12",
    "agreements": [
      {"type":"SERVICE_TERMS","version":"v1.0","agreed":true},
      {"type":"PRIVACY_POLICY","version":"v1.0","agreed":true}
    ]
  }')
echo "$RESP" | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["accessToken"])' \
  > /tmp/k6-token
cat /tmp/k6-token   # 토큰 확인
```
> 이메일은 중복 불가라 매번 다르게 (`k6-tester2`, `k6-tester3` …).

> **참고:** accessToken은 기본 30분 유효. 장시간 테스트 중엔 새로 발급 권장.

### 2-2. 시나리오 실행

```bash
cd docs/perf/k6
export TOKEN=$(cat /tmp/k6-token)
export BASE_URL="http://localhost:8080"

# 4개 중 하나 선택
k6 run feed.js
k6 run search.js
k6 run detail.js
k6 run reaction.js
```

### 2-3. 결과 비교용 JSON 저장

baseline을 덮어쓰지 않으려면 다른 경로로:

```bash
mkdir -p ../results/L   # L 세트 결과
k6 run --summary-export=../results/L/feed.json feed.js
k6 run --summary-export=../results/L/search.json search.js
k6 run --summary-export=../results/L/detail.json detail.js
k6 run --summary-export=../results/L/reaction.json reaction.js
```

### 2-4. 주목해서 볼 지표

k6 출력의 `HTTP` 블록:

```
http_req_duration......: avg=XX  med=YY  p(90)=ZZ  p(95)=AA  p(99)=BB
```

**p(95), p(99)** 만 주로 보세요. 임계값(threshold) 통과 여부는 맨 위 `THRESHOLDS` 섹션에 ✓/✗로 요약.

baseline 수치 (S 세트 결과):
- feed p95=184ms / p99=307ms
- search p95=168ms / p99=212ms
- detail p95=280ms / p99=387ms
- reaction p95=152ms / p99=190ms

**L 세트에서 수치가 얼마나 튀는지 관찰** — 선형으로 커지면 인덱스 OK, 비선형으로 폭증하면 Full Scan 가능성.

---

## Step 3. DBeaver에서 쿼리 분석

### 3-1. 연결 세팅 (처음 한 번)

DBeaver → New Database Connection → PostgreSQL:

| 필드 | 값 |
|------|-----|
| Host | `localhost` |
| Port | `55432` |
| Database | `builders` |
| Username | `builders` |
| Password | `builders` |

Test Connection → Finish.

### 3-2. 핵심 쿼리 4개 EXPLAIN ANALYZE로 분석

DBeaver SQL Editor에서 **하나씩 실행**하고 결과(Query Plan 탭)를 보세요.

#### ① 피드 (커서 기반 — LATEST)

```sql
EXPLAIN ANALYZE
SELECT * FROM therapy_posts
WHERE deleted_at IS NULL
  AND visibility = 'PUBLIC'
  AND (created_at, id) < ('2026-03-20 10:00:00'::timestamp, 9999999)
ORDER BY created_at DESC, id DESC
LIMIT 21;
```

**확인할 것:**
- `Index Scan` 사용하는지 (Seq Scan 아닌지)
- `Rows Removed by Filter` 적은지
- `Execution Time` ms 값

#### ② 피드 (인기순)

```sql
EXPLAIN ANALYZE
SELECT * FROM therapy_posts
WHERE deleted_at IS NULL
  AND visibility = 'PUBLIC'
  AND (popularity_score, id) < (1000, 9999999)
ORDER BY popularity_score DESC, id DESC
LIMIT 21;
```

**확인할 것:**
- `idx_therapy_posts_popularity_score_id` 인덱스 사용?

#### ③ 게시글 상세 — 반응 카운트

```sql
EXPLAIN ANALYZE
SELECT r.reaction_type, COUNT(*)
FROM therapy_post_reactions r
WHERE r.post_id = 100     -- 아무 PUBLIC 게시글 id
GROUP BY r.reaction_type;
```

**확인할 것:**
- `idx_therapy_post_reactions_post_id` (UNIQUE 제약으로 자동) 사용?
- `HashAggregate` 메모리 사용량

#### ④ 피드 목록 LIKE count 배치 조회 (PR #62 추가분)

```sql
EXPLAIN ANALYZE
SELECT r.post_id, COUNT(*)
FROM therapy_post_reactions r
WHERE r.post_id IN (1, 50, 100, 1000, 5000, 10000, 50000)
  AND r.reaction_type = 'LIKE'
GROUP BY r.post_id;
```

**확인할 것:**
- `Index Scan` + `Bitmap Heap Scan` 조합?
- IN 내 각 id별 plan이 일관적인지

### 3-3. 슬로우 쿼리 직접 보기 — auto_explain 로그

k6 테스트 돌린 직후:

```bash
docker logs builders-db 2>&1 | grep -E "duration: [0-9]+" | tail -20
```

숫자가 ms 기준. 50ms 이상 쿼리가 잡혀 있다면:

```bash
# 전체 plan 포함 로그 보기
docker logs builders-db 2>&1 | grep -A 20 "duration:" | less
```

**읽는 방법:**
- `Seq Scan on XXX` → 테이블 전체 스캔 (인덱스 없거나 활용 안 됨)
- `Index Scan using YYY` → 인덱스 활용 중 ✓
- `rows=N` → 해당 단계에서 반환된 실제 행 수
- `actual time=X..Y` → 첫 행 반환까지 X ms, 전체 Y ms

## Step 4. 병목 찾으면 기록

관찰 결과를 아래 포맷으로 `docs/perf/REPORT.md`에 정리 (없으면 새로 만들기):

```markdown
## 볼륨 L (posts 100k / reactions 7M) — 2026-04-22

### k6 결과
| 시나리오 | p95 | p99 | failure |
|----------|-----|-----|---------|
| feed | XXXms | XXXms | X% |
| ... | ... | ... | ... |

### 슬로우 쿼리 TOP N
1. [SQL] ... — 000ms — 원인: Seq Scan on therapy_posts
2. ...

### 가설 개선안
- A: therapy_posts에 (visibility, created_at) 복합 인덱스 추가
- B: comments countByPostIdIn을 materialized view로
```

---

## 부록 1. 환경 재기동

### DB/Redis 띄우기

```bash
docker compose -f docker-compose.yml -f docker-compose.perf.yml up -d db redis
```

### 백엔드 띄우기 (로컬 Docker DB에 연결)

```bash
SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:55432/builders' \
SPRING_DATASOURCE_USERNAME='builders' \
SPRING_DATASOURCE_PASSWORD='builders' \
REDIS_HOST='localhost' \
REDIS_PORT='6379' \
./gradlew bootRun --args='--spring.profiles.active=local,perf --spring.sql.init.mode=never'
```

### 완전 초기화 (Flyway checksum 에러 등 발생 시)

```bash
docker compose down
docker volume rm backend-now_builders-postgres-data
docker compose -f docker-compose.yml -f docker-compose.perf.yml up -d db redis
# → 백엔드 재기동 → seed 재주입
```

---

## 부록 2. auto_explain threshold 조정

기본 50ms. 더 많은 쿼리 잡고 싶으면:

```bash
docker exec builders-db psql -U builders -d builders -c \
  "ALTER SYSTEM SET auto_explain.log_min_duration = '10ms';"
docker exec builders-db psql -U builders -d builders -c "SELECT pg_reload_conf();"
```

---

## 부록 3. 자주 쓰는 psql 명령

```bash
# 접속
docker exec -it builders-db psql -U builders -d builders

# 테이블 목록
\dt

# 인덱스 보기
\di

# 특정 테이블 구조
\d therapy_posts

# row 수
SELECT COUNT(*) FROM therapy_posts;

# 종료
\q
```

---

## Troubleshooting

| 증상 | 원인 / 해결 |
|------|------------|
| `Flyway checksum mismatch` | volume 삭제 후 재기동 (부록 1) |
| k6 `TOKEN env required` | `export TOKEN=$(cat /tmp/k6-token)` 먼저 |
| `INVALID_CREDENTIALS` 로그인 실패 | seed user는 bcrypt 해시 더미라 로그인 안 됨. 회원가입 API 사용 (Step 2-1) |
| `http_req_failed rate=15%` (detail) | PRIVATE 게시글 403. 의도한 동작 |
| `CONFLICT` 회원가입 | 같은 이메일 존재. `k6-tester2`, `k6-tester3`… 순차 증가 |
| 백엔드 재기동 시 토큰 무효 | JWT secret이 바뀌지 않으면 유효. 서명 secret 변경 시 재발급 필요 |
