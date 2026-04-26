# N+1 Before 측정 결과 (experiment/n-plus-1-demo 브랜치)

## 환경
- 브랜치: experiment/n-plus-1-demo
- 데이터: L 세트 (users 10k / posts 100k / comments 1M / reactions 7M)
- 시나리오: feed.js (30 VU × 60s)
- 측정 시각: 2026-04-23 03:56

## k6 결과
| 지표 | 값 |
|------|-----|
| p95 http_req_duration | **344.95 ms** |
| p90 | 298.73 ms |
| med | 175.29 ms |
| max | 1.31 s |
| throughput | 121.0 req/s |
| iterations/s | 40.3 |
| failure rate | 0.00% |

## Hibernate Session Metrics
- 요청당 JDBC statements executed: **43**
  (posts 1 + reaction count 20 + comment count 20 + scrap IN 1 + etc 1)

## 원인 코드
PostService.toSummaries() — per-post 루프에서 reaction/comment count 개별 쿼리.
