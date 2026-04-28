# N+1 After 측정 결과 (chore/perf-toolkit / main 기준 batch 쿼리)

## 환경
- 브랜치: chore/perf-toolkit (main 기반)
- 데이터: L 세트 (users 10k / posts 100k / comments 1M / reactions 7M)
- 시나리오: feed.js (30 VU × 60s)
- 측정 시각: 2026-04-23 04:01

## k6 결과
| 지표 | 값 |
|------|-----|
| p95 http_req_duration | **49.85 ms** |
| p90 | 41.11 ms |
| med | 22.06 ms |
| max | 572.34 ms |
| throughput | 391.9 req/s |
| iterations/s | 130.7 |
| failure rate | 0.00% |

## Hibernate Session Metrics (요청당)
- main 세션: **3 JDBC statements** (posts 1 + reaction IN batch 1 + comment IN batch 1)
- scrap 조회 세션: **1 JDBC statement**
- 합계: **4회**

## 핵심 코드
`PostService.toSummaries()` — postIds 수집 후 `countByPostIdInAndReactionType`, `countActiveByPostIdIn` 배치 쿼리.
