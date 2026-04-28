# k6 시나리오

로컬 성능 측정용 k6 테스트 스크립트.

## 설치

```bash
# macOS
brew install k6

# 버전 확인
k6 version  # v0.40 이상 권장
```

## 시나리오 목록

| 파일 | 대상 엔드포인트 | 의도 |
|------|----------------|------|
| `feed.js` | `GET /api/v1/posts/feed` | 무한스크롤 커서 페이지네이션. N+1 / grouped count 배치 |
| `search.js` | `GET /api/v1/posts` | 키워드 검색. pg_trgm / LIKE 인덱스 활용 |
| `detail.js` | `GET /api/v1/posts/{id}` | 상세 조회. reaction grouped count + comment count |
| `reaction.js` | `PUT /api/v1/posts/{id}/reaction` | 쓰기 부하. 토글 후 popularity_score 재계산 |

## 실행 전 준비

1. **Seed data** 주입: [../README.md#1-대용량-seed-data-주입](../README.md#1-대용량-seed-data-주입)
2. **백엔드 기동**:
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local'
   ```
3. **인증 토큰 발급** (아래 "토큰 발급" 참조)

## 토큰 발급

k6 스크립트는 env 변수로 토큰을 받습니다. 로컬에서 로그인해서 accessToken 발급:

```bash
# 로컬 seed user로 로그인 — email 'perf-500@test.local' / password 'password'
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"perf-500@test.local","password":"password"}' \
  | jq -r '.data.tokens.accessToken')

echo $TOKEN
```

⚠️ seed user의 password_hash는 bcrypt('password')로 고정돼 있습니다. 로컬 전용.

## 실행

```bash
# 단일 시나리오
k6 run -e TOKEN="$TOKEN" -e BASE_URL="http://localhost:8080" feed.js

# 리포트 파일 저장
k6 run --out json=result-feed.json feed.js

# VU 조절 (기본값은 각 스크립트 내 options)
k6 run --vus 50 --duration 60s feed.js
```

## 측정 지표

각 스크립트 출력에서 주목할 것:

| 지표 | 의미 | 목표 |
|------|------|------|
| `http_req_duration` p(95) | 95퍼센타일 응답시간 | < 500ms |
| `http_req_duration` p(99) | 99퍼센타일 | < 1000ms |
| `http_req_failed` rate | 실패율 | < 1% |
| `iterations/s` | 처리량 (req/s) | 시나리오 별 |
| `checks` | 응답 검증 통과율 | 100% |

k6 출력의 요약 테이블 외에, slow query는 PostgreSQL `auto_explain` 로그에서 교차 확인.

## Baseline → 개선 → 재측정

1. 개선 전 k6 run → `result-baseline.json` 저장
2. 인덱스 추가 / 쿼리 수정 PR 작성
3. 동일 조건 k6 run → `result-after.json` 저장
4. p95·p99·throughput 비교 기록 → `docs/perf/REPORT.md`
