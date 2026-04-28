# 관찰성 세팅 가이드

성능 측정 중 쿼리 플랜, 응답시간, Hibernate 통계를 얻기 위한 설정.

## 1. 백엔드 — `perf` 프로파일

`application-perf.yaml`이 `local` 위에 덮어씌우는 구조입니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local,perf'
```

켜지는 항목:
- Hibernate SQL 쿼리 로그 (`DEBUG`)
- 바인딩 파라미터 로그 (`TRACE`)
- Hibernate 통계 (쿼리 카운트, 캐시 히트/미스)
- Actuator endpoints: `/actuator/health`, `/actuator/metrics`
- HTTP 요청 latency 히스토그램 + p50/p95/p99 + SLO 버킷

### 주요 Actuator URL
- 전체 메트릭 목록: `http://localhost:8080/actuator/metrics`
- HTTP 요청 latency: `http://localhost:8080/actuator/metrics/http.server.requests`
- DataSource 연결: `http://localhost:8080/actuator/metrics/hikaricp.connections.active`
- JVM 힙: `http://localhost:8080/actuator/metrics/jvm.memory.used`

## 2. PostgreSQL — `auto_explain` 활성화

slow query plan을 DB 로그에 자동 기록합니다. `EXPLAIN ANALYZE`를 수동으로 돌리지 않아도 병목 발견 가능.

### 적용 방법

`docker-compose.perf.yml`이 `docker-compose.yml`을 덮어써서 `auto_explain` 확장을 주입합니다. 기존 개발용 compose는 건드리지 않습니다.

```bash
# perf 모드로 db 컨테이너만 기동
docker compose -f docker-compose.yml -f docker-compose.perf.yml up -d db
```

설정 항목 (slow query 기준 **50ms 이상**만 로그 남김):
- `shared_preload_libraries=auto_explain` — 확장 로드
- `auto_explain.log_min_duration=50ms` — 이 값 이상 쿼리만
- `auto_explain.log_analyze=true` — 실행 시간·rows 포함
- `auto_explain.log_buffers=true` — 버퍼 hit/read 통계

### 로그 확인

```bash
docker logs builders-db 2>&1 | grep -A 30 "auto_explain" | less
```

또는 로그 파일로 따로 빼기:

```bash
docker logs builders-db > /tmp/pg-auto-explain.log 2>&1
```

## 3. Hibernate 통계 (런타임)

`generate_statistics=true`면 애플리케이션 로그에 주기적으로 나옵니다:

```
Session Metrics {
    40000 nanoseconds spent acquiring 1 JDBC connections;
    ...
    42 JDBC statements executed;
}
```

N+1 판별: 엔드포인트 한 번 호출에 **"X JDBC statements executed"** 값이 비정상적으로 크면 의심.

## 4. k6 결과와 교차 검증

k6로 측정 후:

| k6 결과 | 의미 | 교차 확인 위치 |
|--------|------|---------------|
| p95 latency 높음 | slow endpoint | auto_explain 로그에서 50ms+ 쿼리 확인 |
| p99 꼬리 급증 | 특정 쿼리 타임아웃 유사 | actuator/metrics의 SLO 버킷 |
| iterations/s 낮음 | throughput 부족 | Hikari 연결 pool 포화? |

## 5. 다음 단계

baseline 측정 → `auto_explain` 로그에서 slow query TOP 10 뽑기 → 개선 대상 선정.
`docs/perf/REPORT.md`에 결과 기록.
