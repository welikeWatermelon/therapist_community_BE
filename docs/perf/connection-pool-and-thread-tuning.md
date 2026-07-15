# Tomcat 스레드 & Hikari 커넥션 풀 튜닝

> 작성 배경: 운영 안정성 + "기본기" 정리용. 임의의 숫자를 박지 않고 **실측값에서 역산**한다.
> 측정일 기준 라이브 환경: EC2 t3.small 1대 + RDS PostgreSQL(`therapist-community-dev`, db.t3.micro).

---

## 0. 결론 먼저 (TL;DR)

```yaml
# application.yaml (공통 baseline)
server:
  tomcat:
    threads:
      max: 50          # 동시 처리 워커 상한
      min-spare: 10    # 상시 대기 워커
    accept-count: 100  # 워커/커넥션 다 찼을 때 OS 대기 큐 길이
    max-connections: 200  # 동시에 "받아들이는" 소켓 상한

spring:
  datasource:
    hikari:
      maximum-pool-size: 10     # 인스턴스당 DB 커넥션 상한
      minimum-idle: 10          # 고정 크기 풀(생성 지연 제거)
      connection-timeout: 3000  # 풀 고갈 시 대기 한도(ms) → fail-fast
      max-lifetime: 1740000     # 29분. 인프라 idle timeout보다 짧게
      keepalive-time: 120000    # 2분마다 idle 커넥션 핑(끊김 방지)
      validation-timeout: 1000  # 커넥션 살아있는지 검사 한도(connection-timeout보다 작아야)
```

> `server.tomcat.mbeanregistry.enabled: true`도 함께 켠다(아래 6장 diff). 안 켜면
> `tomcat_threads_*` 메트릭이 노출되지 않아 스레드 포화를 모니터링할 수 없다(실측에서 확인됨).

핵심은 숫자 자체보다 **왜 이 숫자인지**다. 아래에서 전부 역산한다.

---

## 0.5. 변경 값 한눈에 보기 (전 → 후 비교)

> "전(前)"은 yml에 설정이 없어서 적용되던 **프레임워크 기본값**이다.
> 즉 "값을 바꿨다"기보다 **암묵적 기본값을 의도된 값으로 명시·조정**한 것.

### Hikari (DB 커넥션 풀)

| 설정 | 전(기본) | 후 | 무슨 값인가 | 왜 이렇게 바꿨나 | 효과 |
|---|---|---|---|---|---|
| `maximum-pool-size` | 10 | **10** | 인스턴스가 동시에 열어두는 DB 커넥션 최대 수 | 실측 `active=0/pending=0` + db.t3.micro 처리한계(~5)라 10이 이미 적정 → 값은 두고 의도를 명시 | dev+prod 합산 20/81 보장, "왜 10인지" 근거가 코드에 남음 |
| `minimum-idle` | 10 | **10** | 안 써도 유지하는 최소 idle 커넥션 수 | max와 같게 둬 고정 크기 풀 구성 | 트래픽 올 때 커넥션 생성 지연(수십 ms) 없음 |
| `connection-timeout` | 30000(30초) | **3000(3초)** | 풀이 꽉 찼을 때 커넥션을 기다리는 최대 시간 | 30초 대기는 스레드를 오래 묶어 장애를 번지게 함 → fail-fast | 3초 만에 실패·스레드 반납 → 장애 격리, 박스 보호 |
| `max-lifetime` | 1800000(30분) | **1740000(29분)** | 커넥션 1개의 최대 수명(넘으면 폐기·재생성) | 인프라(NAT/방화벽/RDS) idle timeout보다 짧게 | "죽은 커넥션"을 손에 쥘 위험 제거 → connection reset 예방 |
| `keepalive-time` | 0(비활성) | **120000(2분)** | idle 커넥션에 주기적으로 보내는 생존 핑 | RDS가 네트워크 너머라 idle 끊김 위험 | 오래 안 쓴 커넥션도 살아있음 보장 |
| `validation-timeout` | 5000(5초) | **1000(1초)** | 빌려주기 전 커넥션 유효성 검사 제한시간 | 반드시 `connection-timeout`(3초)보다 작아야 함 | 빠른 검증 + 설정 정합성 확보 |

### Tomcat (요청 처리 스레드)

| 설정 | 전(기본) | 후 | 무슨 값인가 | 왜 이렇게 바꿨나 | 효과 |
|---|---|---|---|---|---|
| `threads.max` | 200 | **50** | 동시에 요청을 처리하는 워커 스레드 최대 수 | 메모리 빠듯한 박스(스택 ~1MB×200≈200MB) + 실측 트래픽 낮음(피크 ~14rps) | 메모리 ~150MB 절약, 컨텍스트 스위칭 감소 |
| `threads.min-spare` | 10 | **10** | 미리 떠 있는 대기 워커 수 | 기본 유지(적정) | 트래픽 와도 스레드 생성 없이 즉시 처리 |
| `accept-count` | 100 | **100** | 워커·소켓 다 찼을 때 OS 대기 큐 길이 | 값은 적정, 의도를 명시 | 큐 길이 통제, 초과 시 명확히 연결 거절 |
| `max-connections` | 8192 | **200** | 톰캣이 동시에 받아들이는 소켓 최대 수 | 8192는 작은 박스에 과함(소켓 누적 시 메모리 압박) | 과도한 소켓 적체로 인한 OOM 위험 차단 |
| `mbeanregistry.enabled` | false | **true** | 톰캣 MBean 등록 = 스레드풀 메트릭 노출 스위치 | 끄면 `tomcat_threads_*` 메트릭이 안 나와 관측 불가(실측서 확인) | 스레드 포화를 모니터링·검증 가능해짐 |

**한 줄 효과 요약**: 풀 크기(처리량)는 측정으로 "이미 충분"이 확인돼 그대로 두고,
바뀐 건 전부 **안정성·관측성** 쪽이다 — 장애 전파 차단(timeout↓), 네트워크 끊김 방지(lifetime/keepalive),
메모리 보호(threads·max-connections↓), 모니터링 가능(mbeanregistry). 처리량은 안 건드리므로 기존 동작이 깨질 위험이 낮다.

---

## 0.6. 각 설정 자세히 (역할 · 동작 · 바꿔서 얻는 이점)

> 표만으론 부족하니 설정별로 "이게 무슨 일을 하는 부품인가 → 어떻게 동작하나 →
> 기본값이면 뭐가 문제인가 → 바꿔서 얻는 이점"을 풀어서 설명한다.

### A. Hikari — DB 커넥션 풀

먼저 풀(pool)이라는 개념. DB 커넥션 하나를 새로 맺는 건 비싸다(TCP 연결 + 인증 + 세션 셋업 = 수십 ms).
요청마다 새로 맺으면 느리고 DB도 부담이다. 그래서 Hikari가 커넥션을 **미리 만들어 모아두고(=풀)**
요청이 오면 빌려주고 끝나면 반납받아 재사용한다. 아래 값들은 그 "커넥션 창고"의 운영 규칙이다.

#### `maximum-pool-size` (풀의 최대 화구 수)
- **역할**: 이 인스턴스가 DB에 동시에 열어둘 수 있는 커넥션의 상한. 곧 **동시에 진행 가능한 DB 작업 수**의 천장.
- **동작**: 요청이 커넥션을 빌리려 할 때 여유분이 있으면 즉시 빌려주고, 다 나갔으면 누가 반납할 때까지 기다린다(아래 `connection-timeout`만큼). 풀은 이 값을 넘겨 커넥션을 만들지 않는다.
- **기본값(10)이면?**: 사실 이 프로젝트에선 10이 적정이라 문제는 없다. 다만 "왜 10인지"가 코드에 안 남아 있어, 누군가 무심코 키우면 공유 RDS(81)를 넘길 위험이 있다.
- **바꿔서 얻는 이점**: 값을 10으로 **명시**하면서 근거(실측 0 사용 + db.t3.micro 한계 ~5 + dev/prod 합산 제약)를 함께 박아둔다. → 실수로 과증설하는 사고를 막고, 처리량 한계를 팀이 명확히 인지한다.

#### `minimum-idle` (평소 켜둘 최소 화구 수)
- **역할**: 트래픽이 없어도 항상 유지할 idle(놀고 있는) 커넥션의 최소 개수.
- **동작**: max(10)와 같게 두면 풀이 **고정 크기**가 된다. 항상 10개가 떠 있고, 줄었다 늘었다 하지 않는다.
- **기본값이면?**: Hikari 기본도 `minimum-idle = maximum-pool-size`라 동일하게 동작한다. 다만 명시하지 않으면 의도가 드러나지 않는다.
- **바꿔서 얻는 이점**: 고정 크기 풀은 **트래픽이 갑자기 와도 커넥션을 새로 만드느라 생기는 지연(수십 ms 스파이크)이 없다.** DB가 보는 커넥션 수도 일정해 모니터링·용량계획이 쉬워진다. (단점: 놀 때도 10개를 점유 → 공유 DB 부담이 커지면 5로 낮추는 절충 가능)

#### `connection-timeout` (화구 빌 때까지 기다리는 한도)
- **역할**: 풀이 꽉 찼을 때, 요청 스레드가 커넥션을 **얼마나 기다렸다가 포기할지**.
- **동작**: 풀에 여유가 없으면 스레드는 여기 적힌 시간만큼 블로킹된다. 그 안에 누가 반납하면 진행, 못 받으면 `SQLException`을 던지고 요청은 실패(보통 500)하지만 **스레드는 즉시 풀려난다.**
- **기본값(30초)이면?**: DB가 포화됐을 때 워커 스레드가 **30초씩 묶인다.** 50개 워커가 전부 30초 대기에 빠지면 새 요청은 줄줄이 쌓이고 → 메모리 압박 → **장애가 서버 전체로 번진다(cascading failure).**
- **바꿔서 얻는 이점(30초→3초)**: 풀이 막혀도 **3초 만에 빠르게 실패**시키고 스레드를 회수한다. 사용자 일부에겐 에러가 나가지만 **서버는 살아남아 곧 회복한다.** "느리지만 죽지는 않는" 상태 = 장애 격리. 메모리 빠듯한 공유 박스에 특히 중요.

#### `max-lifetime` (커넥션 1개의 최대 수명)
- **역할**: 커넥션 하나를 만든 뒤 **얼마나 오래 쓰다가 폐기·재생성**할지.
- **동작**: 이 시간을 넘긴 커넥션은 (사용 중이 아닐 때) 풀이 조용히 닫고 새로 만든다. 항상 "신선한" 커넥션만 돌게 한다.
- **기본값(30분)이면?**: 커넥션 경로 어딘가(방화벽, NAT 게이트웨이, RDS 서버측)에 "일정 시간 조용하면 끊는" 타이머가 있다. 인프라가 먼저 끊으면 풀은 그걸 모르고 **죽은 커넥션을 앱에 빌려준다 → 첫 쿼리에서 `connection reset` 에러.**
- **바꿔서 얻는 이점(29분)**: 인프라 타이머보다 **앱이 먼저** 커넥션을 갈아끼우게 해서, 항상 살아있는 커넥션만 손에 쥔다. RDS가 네트워크 너머라(같은 박스의 Redis와 달리) 이 보호가 실제로 의미 있다.

#### `keepalive-time` (idle 커넥션 생존 핑)
- **역할**: 놀고 있는 커넥션이 끊기지 않도록 **주기적으로 보내는 가벼운 신호(ping)**.
- **동작**: 지정 주기마다 idle 커넥션에 가벼운 검증 쿼리를 날려 "나 아직 살아있다"고 알린다. NAT/방화벽의 idle 타이머가 리셋된다.
- **기본값(0=비활성)이면?**: 핑을 안 보내므로, 트래픽이 한산한 새벽 같은 때 오래 idle 상태인 커넥션이 인프라에 의해 조용히 끊길 수 있다.
- **바꿔서 얻는 이점(2분)**: idle 커넥션도 2분마다 깨워주니, **오래 안 쓴 커넥션을 다음에 빌려줘도 살아있다.** `max-lifetime`이 "수명 다 된 커넥션 교체"라면, 이건 "쉬는 커넥션이 끊기지 않게 유지"하는 보완재.

#### `validation-timeout` (빌려주기 전 점검 한도)
- **역할**: 커넥션을 요청에 내주기 직전 "이거 살아있나?" 검사에 쓰는 **최대 시간**.
- **동작**: 빌려주기 전 짧은 검증을 하는데, 그 검증이 이 시간을 넘기면 해당 커넥션을 죽은 걸로 보고 버린 뒤 다른 걸 시도한다.
- **기본값(5초)이면?**: 우리는 `connection-timeout`을 3초로 줄였는데, 검증 한도(5초)가 그보다 길면 논리가 어긋난다(Hikari 규칙상 검증 한도는 connection-timeout보다 작아야 함).
- **바꿔서 얻는 이점(1초)**: 검증을 빠르게 끝내 **설정 간 정합성**을 맞추고, 죽은 커넥션을 빨리 걸러낸다.

### B. Tomcat — 요청 처리 스레드

톰캣은 들어온 HTTP 요청을 **워커 스레드**에 하나씩 배정해 처리한다(종업원 비유). 아래 값들은 종업원을
몇 명까지 둘지, 손님이 몰리면 어디까지 받고 어디서 돌려보낼지의 규칙이다.

#### `threads.max` (동시 처리 워커 최대 수)
- **역할**: 동시에 요청을 처리할 수 있는 워커 스레드의 상한 = **동시 처리량의 천장.**
- **동작**: 요청이 오면 여유 워커에 배정한다. 다 바쁘면 신규 요청은 아래 큐(`accept-count`)에서 대기한다.
- **기본값(200)이면?**: 스레드 1개당 스택 메모리가 ~1MB라 200개면 **~200MB를 스레드만으로 점유**한다. 이 박스는 힙 768MB에 Redis·Grafana·Prometheus까지 같이 떠서 메모리가 빠듯한데, 거기에 200스레드는 과하다. 게다가 2 vCPU라 200개가 동시에 돌면 컨텍스트 스위칭 낭비만 커진다.
- **바꿔서 얻는 이점(200→50)**: 50×~1MB ≈ 50MB만 쓴다(약 150MB 절약). 실측 트래픽이 피크 ~14rps라 50으로도 차고 넘친다. **메모리 안정화 + CPU 효율** 둘 다 챙긴다. 핵심: DB 풀이 10이라 어차피 DB 작업은 동시에 10개뿐 → 스레드를 200까지 둘 이유가 없다.

#### `threads.min-spare` (상시 대기 워커 수)
- **역할**: 트래픽이 없어도 미리 떠 있는 예비 워커 수.
- **동작**: 요청이 갑자기 와도 새 스레드를 만드는 지연 없이 즉시 처리하도록, 항상 이만큼은 준비해 둔다.
- **기본값(10)이면?**: 적정이라 문제없다.
- **바꿔서 얻는 이점**: 10으로 유지 → 트래픽 급증 초기에도 **스레드 생성 지연 없이 바로 응답.** (값 유지지만 의도를 명시)

#### `accept-count` (대기 큐 길이)
- **역할**: 워커도 다 차고 소켓도 다 찼을 때, **OS 레벨에서 줄 세워두는 대기 큐의 길이.**
- **동작**: 처리할 워커가 없으면 신규 연결을 이 큐에 넣어둔다. 큐가 비는 대로 처리하고, **큐마저 꽉 차면 OS가 새 연결을 거절**(클라이언트는 connection refused)한다.
- **기본값(100)이면?**: 적정이지만 명시하지 않으면 "얼마나 버티다 거절하는지"가 불명확하다.
- **바꿔서 얻는 이점**: 100으로 명시 → **과부하 시 거절 임계점을 의도적으로 통제.** 무한정 받아 쌓이는 대신, 일정 선을 넘으면 깔끔히 거절해 서버를 보호한다(앞의 fail-fast 철학과 일관).

#### `max-connections` (동시 수용 소켓 상한)
- **역할**: 톰캣이 **동시에 받아들여 들고 있는** 연결(소켓)의 최대 수. 처리 중 + keep-alive 대기까지 포함.
- **동작**: 이 수에 도달하면 그 이상은 새로 accept하지 않고 `accept-count` 큐에서 대기시킨다.
- **기본값(8192)이면?**: 작은 박스가 한 번에 8192개 소켓을 들고 있으려다 **메모리·파일디스크립터를 과하게 점유**할 수 있다. 트래픽 규모에 안 맞는 과대 설정.
- **바꿔서 얻는 이점(8192→200)**: 박스 체급에 맞춰 소켓 적체 상한을 낮춰 **메모리 압박과 소켓 누적으로 인한 OOM 위험을 차단.** 동시 200소켓이면 현재 트래픽 대비 충분한 여유.

#### `mbeanregistry.enabled` (스레드 메트릭 노출 스위치)
- **역할**: 톰캣 내부 지표(스레드풀 등)를 MBean으로 등록할지. 이게 켜져야 Micrometer가 `tomcat_threads_*` 메트릭을 수집한다.
- **동작**: true면 톰캣이 ThreadPool 등의 MBean을 등록 → `/actuator/prometheus`에 `tomcat_threads_busy_threads` 등이 노출된다.
- **기본값(false)이면?**: **스레드 관련 메트릭이 아예 안 나온다.** 실측에서도 이 때문에 스레드 지표가 비어 나왔다. → 스레드가 포화됐는지 관측할 방법이 없다.
- **바꿔서 얻는 이점(true)**: 스레드 busy/현재/최대 수를 모니터링할 수 있게 된다. **threads.max=50이 적정한지 데이터로 검증**할 수 있고, 향후 트래픽 증가 시 포화 여부를 조기에 감지한다. (튜닝의 "검증" 단계가 비로소 가능해짐)

---

## 1. 현재 상태 진단

### 1-1. yml에 빠져 있어 전부 "기본값"으로 도는 항목

| 항목 | 현재 설정 | 미설정 시 실제 동작값 | 안 잡으면 생기는 문제 |
|---|---|---|---|
| `server.tomcat.threads.max` | 없음 | **200** | 2 vCPU/메모리 빠듯한 박스에서 스레드 200개는 과함(스택 메모리·컨텍스트 스위칭 낭비) |
| `tomcat.threads.min-spare` | 없음 | 10 | (영향 작음) |
| `tomcat.accept-count` | 없음 | 100 | 큐 길이를 의식적으로 못 정함 |
| `tomcat.max-connections` | 없음 | **8192** | 작은 박스가 소켓 8192개를 받으려다 메모리 압박 |
| Hikari `maximum-pool-size` | 없음 | **10** | (실측: dev_user 11개 = 풀 10+1) |
| Hikari `minimum-idle` | 없음 | = pool(10) | (영향 작음) |
| Hikari `connection-timeout` | 없음 | **30000(30초)** | 풀 고갈 시 요청이 30초나 블로킹 → 스레드 줄줄이 묶여 장애 전파 |
| Hikari `max-lifetime` | 없음 | 1800000(30분) | 인프라 idle timeout보다 길면 "죽은 커넥션"을 손에 쥘 위험 |
| Hikari `keepalive-time` | 없음 | 0(비활성) | NAT/방화벽이 idle 커넥션을 조용히 끊어도 모름 |

### 1-2. 이미 보이는 모순

Tomcat은 기본 **200개 요청을 동시에** 처리하려 하는데, DB 풀은 **10개**다.
DB를 건드리는 요청이 11개만 동시에 와도 11번째부터 풀에서 줄을 서고(기본 30초 대기 후 실패),
**실질 동시처리 한계는 톰캣 200이 아니라 Hikari 10이 결정**한다. 둘의 균형을 맞추는 게 이 작업의 목적이다.

### 1-3. 환경별 차이 (튜닝 관점)

- **application.yaml(공통)**: JPA `open-in-view:false`, `batch_size:50`, `order_inserts:true`, Redis `timeout:2s`/`connect-timeout:1s` — 기본기는 잘 잡혀 있음. **스레드/풀 설정은 없음.**
- **dev / prod**: `datasource.url`·계정만 환경변수로 다름. 쿠키 `same-site`(dev=None, prod=Lax)·S3 버킷 차이. **스레드/풀 튜닝은 어느 쪽에도 없음.**
- **perf**: 로깅·메트릭 측정용. 풀/스레드 설정 없음.

### 1-4. 실서버 실측에서 드러난 핵심 사실 ⚠️

`pg_stat_activity` 조회 결과:

```
max_connections                = 81
superuser_reserved_connections = 3
current_conns                  = 29

usename            | count
-------------------+------
therapy_dev_user   |  11   ← 우리 백엔드(Hikari 기본 10 + 1)
therapy_prod_user  |  10   ← prod도 같은 RDS에 붙어 있음 (!)
(background)        |   5   ← autovacuum / 복제 / 내부
rdsadmin           |   3   ← RDS 관리(superuser 예약분)
```

- **dev와 prod는 데이터베이스가 분리**(`therapy_dev`/`therapy_prod`)돼 데이터는 격리되지만,
  **같은 RDS 인스턴스 1대(`max_connections=81`)** 위에 있어 커넥션 한도를 **공유**한다.
  (`max_connections`는 DB 단위가 아니라 인스턴스 단위 설정 → 데이터가 달라도 81을 나눠 씀)
- 즉, 풀 크기는 "이 인스턴스만"이 아니라 **dev 풀 + prod 풀 + 백그라운드 + 예약분이 전부 81 안에 들어가게** 정해야 한다.
- 이걸 모르고 한쪽 풀을 키우면 다른 쪽 커넥션을 굶겨서 `FATAL: sorry, too many clients already`로 앱이 새 연결을 못 맺는다.
- (참고) 라이브는 받은 k8s 매니페스트(`replicas:2`, HPA 2~5)와 달리 **EC2 1대 + 단일 컨테이너**로 동작 중. 컨테이너엔 CPU/메모리 limit 없이 JVM `-Xmx768m`만 걸려 있고, 같은 호스트에 Redis·Prometheus·Grafana·nginx가 함께 떠 있어 **메모리가 빠듯**(1.9GB 중 가용 수백 MB). → 스레드/풀을 **크게가 아니라 작고 명확하게** 가는 게 정답인 환경.

---

## 2. Hikari 풀 크기 역산 (제일 중요)

### 2-1. 천장에서 역산하는 공식

```
인스턴스당 안전 상한
  = (max_connections − superuser_reserved − background − ops_headroom) ÷ (DB를 공유하는 앱 인스턴스 수)
```

실측값 대입:

```
  max_connections            81
− superuser_reserved (rdsadmin 긴급용)        3
− background (autovacuum/복제/내부)           6   (실측 5 + 여유)
− ops_headroom (psql, 부팅 시 Flyway 순간증가,
                모니터링, 수동 쿼리)          12
= 앱이 안전하게 쓸 수 있는 총량              60
÷ 앱 인스턴스 수 (dev 1 + prod 1)              2
= 인스턴스당 안전 상한 ≈ 30
```

→ **풀을 30까지 키워도 81은 안 터진다.** 하지만 30이 "정답"은 아니다. 다음 한 가지를 더 본다.

### 2-2. DB가 실제로 동시에 처리할 수 있는 양 (sweet spot)

커넥션을 많이 열어도 DB의 CPU/디스크가 그만큼 병렬 처리하는 건 아니다. 널리 쓰이는 경험식:

```
최적 동시 커넥션 ≈ (코어 수 × 2) + 유효 디스크 수
db.t3.micro = 2 vCPU  →  (2 × 2) + 1 ≈ 5
```

즉 db.t3.micro는 **5개 안팎**이 진짜 병렬 처리 한계고, 그 이상은 DB 내부에서 어차피 줄을 선다.

### 2-3. 그래서 최종 = 10

- 안전 상한(30)보다 **훨씬 낮게**, DB sweet spot(5)보다는 **약간 위로** 잡아 네트워크 왕복·짧은 버스트를 흡수.
- dev(10) + prod(10) = 20 ≪ 안전 총량 60 → 한쪽이 폭주해도 서로 안 굶김.
- **현재 기본값이 이미 10**이라 값 자체는 안 바뀐다. 핵심 변화는 "10을 명시적으로 문서화 + 아래 timeout/lifetime 안전장치 추가"다.
  → **무작정 키우지 않는 것 자체가 이 DB에선 정답**이라는 게 핵심 근거.

> **측정으로 검증됨**: prod 인스턴스에서 정상 운영 중 스냅샷이
> `hikaricp_connections_active=0, idle=10, pending=0`. 즉 풀 10 중 사용 0·대기 0.
> nginx 피크도 초당 ~14건(상당수 자동 트래픽). → 풀 10이 충분함을 추정이 아니라 데이터로 확인.
> (dev·prod는 각각 별도 EC2 1대씩, 둘 다 풀 10 → 합산 20 / 81. 여유 충분.)

| 항목 | 값 | 근거 |
|---|---|---|
| `maximum-pool-size` | **10** | DB sweet spot(5)의 2배 여유, 안전 상한(30)의 1/3. dev+prod=20<60 |
| `minimum-idle` | **10** | max와 동일 = 고정 크기 풀. 트래픽 올 때 커넥션 새로 만드는 지연(수십 ms) 제거. DB가 보는 커넥션 수가 일정해 예측 가능 |
| `connection-timeout` | **3000ms** | 풀 고갈 시 **3초만 기다리고 실패**. 기본 30초는 스레드를 30초씩 묶어 장애를 전파시킴 → fail-fast로 차단 |
| `max-lifetime` | **1740000ms(29분)** | 커넥션을 주기적으로 폐기·재생성. **DB/LB/NAT의 idle timeout보다 짧아야** 죽은 커넥션을 안 쥠 (아래 2-4) |
| `keepalive-time` | **120000ms(2분)** | idle 커넥션에 2분마다 핑. NAT/방화벽 idle timeout(보통 300~350초)에 걸려 조용히 끊기는 것 방지 |
| `validation-timeout` | **1000ms** | 빌려주기 전 "이 커넥션 살아있나" 검사 한도. 반드시 `connection-timeout`보다 작아야 함(3000>1000 OK) |

> `minimum-idle`을 더 낮춰(예: 5) dev가 놀 때 prod에 커넥션을 양보하는 절충도 가능하다.
> 다만 고정 풀이 지연·예측성 면에서 유리해 기본 권장은 10=10. 공유 부담이 커지면 5로 내려라.

### 2-4. `max-lifetime`을 idle timeout보다 짧게 두는 이유

커넥션 경로 어딘가(방화벽, NAT 게이트웨이, RDS 서버측)에 "일정 시간 조용하면 끊는" 타이머가 있다.
- 인프라가 먼저 끊으면 → 풀은 그 사실을 모르고 **죽은 커넥션을 앱에 빌려줌** → 첫 쿼리에서 `connection reset` 에러.
- `max-lifetime`을 그 타이머보다 짧게 두면 → **앱이 먼저** 커넥션을 폐기·재생성 → 항상 살아있는 커넥션만 손에 쥠.
- AWS NAT/ELB 계열 idle timeout이 보통 350초라 거기에 안 걸리게 `keepalive-time`(2분)으로 핑까지 보낸다. 29분 `max-lifetime`은 RDS 서버측 상한보다 충분히 짧은 보수값.

---

## 3. Tomcat 스레드 역산

### 3-1. `threads.max` = 50

- DB 풀이 10이라 **DB-바운드 요청은 어차피 동시에 10개**만 진짜로 진행된다.
- 하지만 모든 요청이 DB를 치는 건 아님(캐시 hit, health, 정적 응답). 그래서 풀보다는 크게 잡아 비-DB 요청과 짧은 버스트를 받아낸다.
- 상한을 키우는 비용 = **메모리**. 스레드 1개당 스택 ≈ 1MB → 200개면 ~200MB. 호스트가 이미 메모리 빠듯하므로 줄이는 게 곧 안정성.
- 50 × ~1MB ≈ 50MB. 2 vCPU 박스가 감당 가능하고, 풀(10) 위로 충분한 여유.
- 즉 **threads.max(50) > pool(10)**: 요청이 "톰캣 입구"가 아니라 "풀 앞"에서 줄 서게 해 큐 위치를 통제.

### 3-2. `threads.min-spare` = 10

상시 떠 있는 워커. 트래픽이 갑자기 와도 스레드 생성 지연 없이 바로 처리. 기본 10 유지.

### 3-3. `accept-count` = 100, `max-connections` = 200

- `max-connections`: 톰캣이 **동시에 받아들여 들고 있는** 소켓 수(처리 중 + keep-alive 대기 포함). 기본 8192는 작은 박스에 과함 → **200**으로 의식적 제한.
- `accept-count`: 워커도 다 차고 max-connections도 찼을 때, **OS 레벨에서 대기시키는 큐 길이**. 이걸 넘으면 OS가 새 연결을 거절(클라이언트는 connection refused). 기본 **100** 명시.

---

## 4. 과부하 시 동작: 큐 대기 → 거절 흐름

요청이 몰릴 때 어디서 줄 서고 어디서 잘리는지 (위에서 아래로 순서대로):

```
요청 도착
  │
  ├─ 워커 여유 있음(busy < 50)?           → 즉시 처리 ──┐
  │                                                      │
  ├─ 워커 다 참 & 소켓 < max-connections(200)?            │
  │     → 연결은 받아두고 워커 빌 때까지 대기            │
  │                                                      │
  ├─ 소켓도 max-connections(200) 참?                      │
  │     → OS accept 큐에 대기 (accept-count=100까지)      │
  │                                                      │
  └─ accept 큐도 참(100 초과)?                            │
        → OS가 연결 거절 (client: connection refused)     │
                                                          ▼
                                          ┌──────────────────────────┐
                                          │ 워커가 DB 필요 → Hikari   │
                                          │  - 풀 여유(active<10)? 즉시│
                                          │  - 풀 고갈? 최대 3초 대기  │
                                          │     · 3초 내 반납 → 진행   │
                                          │     · 3초 초과 → SQLException│
                                          │       → 500, 워커 즉시 반납 │
                                          └──────────────────────────┘
```

**왜 이렇게 설계하나:**
`connection-timeout`을 3초로 줄였기 때문에, DB가 포화돼도 워커가 3초만 묶였다 풀려난다.
기본 30초였다면 50개 워커가 전부 30초씩 묶여 → 신규 요청은 accept 큐에 쌓이고 → 줄줄이 타임아웃 → **장애 전파**.
fail-fast(3초)로 "느리지만 죽지는 않는" 상태를 만든다. 사용자에겐 일부 500이 나가지만 서버는 살아서 회복한다.

---

## 5. 환경별 배치 (어디에 둘 것인가)

라이브가 단일 EC2(dev 프로파일)라 **공통값을 `application.yaml`에 두는 것**이 가장 단순하고 안전하다.
local/test(H2)에도 무해하고, dev/prod 모두 상속한다.

| 설정 | 위치 | 이유 |
|---|---|---|
| Tomcat 스레드·Hikari 풀 (위 0장 블록 전체) | **application.yaml** | 라이브=dev 프로파일이 상속. 보수적 공통 baseline |
| (선택) prod 전용 RDS로 분리 후 풀 키우기 | application-prod.yaml | **지금은 dev와 같은 RDS 공유**라 키우면 위험. RDS 분리 전엔 override 금지 |
| (선택) 부하테스트용 풀 증설 | application-perf.yaml | 로컬 부하테스트에서만. 공유 RDS 대상으로는 키우지 말 것 |

> 주의: 같은 RDS를 공유하는 한, prod에서 풀을 키우면 dev를, dev에서 키우면 prod를 굶긴다.
> 풀 증설은 **RDS 인스턴스 분리(또는 max_connections 상향)가 선행**돼야 한다.

---

## 6. 변경 전후 diff

### `src/main/resources/application.yaml`

```diff
 spring:
   application:
     name: backend
+  datasource:
+    hikari:
+      # 풀 크기는 RDS max_connections(81)에서 역산.
+      # (81 − 예약3 − 백그라운드6 − 운영여유12) ÷ 앱2(dev+prod 공유) ≈ 30 이 안전 상한.
+      # db.t3.micro(2vCPU) 실제 sweet spot은 (2*2)+1≈5라, 그 2배인 10으로 고정.
+      # dev10 + prod10 = 20 ≪ 60 → 서로 안 굶김.
+      maximum-pool-size: 10
+      minimum-idle: 10          # = max → 고정 크기 풀(커넥션 생성 지연 제거)
+      connection-timeout: 3000  # 풀 고갈 시 3초만 대기 후 실패(기본 30초는 장애 전파)
+      max-lifetime: 1740000     # 29분. DB/NAT idle timeout보다 짧게 → 죽은 커넥션 회피
+      keepalive-time: 120000    # 2분마다 idle 커넥션 핑(NAT/방화벽 끊김 방지)
+      validation-timeout: 1000  # 사용 전 검사 한도(connection-timeout보다 작아야)
   jackson:
     serialization:
       write-dates-as-timestamps: false
@@
 server:
+  tomcat:
+    threads:
+      max: 50          # DB풀(10)보다 크게 → 큐는 '풀 앞'에서. 50*~1MB stack로 메모리도 통제
+      min-spare: 10    # 상시 대기 워커(생성 지연 제거)
+    accept-count: 100  # 워커·소켓 다 찼을 때 OS 대기 큐. 초과 시 연결 거절
+    max-connections: 200  # 동시에 받아들이는 소켓 상한(기본 8192는 작은 박스에 과함)
+    mbeanregistry:
+      enabled: true    # tomcat_threads_* 메트릭 노출(없으면 스레드 포화 관측 불가)
   error:
     whitelabel:
       enabled: false
```

> 기존 `server:` 블록에는 `error:`만 있으니 그 아래에 `tomcat:`을 추가하면 된다(들여쓰기 주의).

**변경 영향 요약**: 풀 크기(10)는 그대로라 기존 동작은 안 깨진다. 실질 변화는
(1) connection-timeout 30초→3초(장애 전파 차단), (2) max-lifetime/keepalive 추가(죽은 커넥션 방지),
(3) Tomcat 스레드 200→50·소켓 8192→200(메모리 안정화)다. 전부 "한도를 명확히 좁히는" 보수적 방향.

---

## 7. 검증 방법 (풀 고갈 / 스레드 포화 확인)

### 7-1. 노출되는 메트릭

`management.endpoints.web.exposure.include`에 `prometheus`가 켜져 있어 Micrometer 지표가 `/actuator/prometheus`로 나간다. 같은 호스트의 **Grafana/Prometheus로 바로 조회 가능**.

핵심 지표:

| 지표 | 의미 | 위험 신호 |
|---|---|---|
| `hikaricp_connections_active` | 사용 중 커넥션 | 지속적으로 `=10`(max) → 풀 포화 |
| `hikaricp_connections_pending` | 커넥션 **기다리는 스레드 수** | `> 0` 지속 → 풀 고갈, 풀이 병목 |
| `hikaricp_connections_timeout_total` | 3초 초과로 실패한 횟수 | 증가 → connection-timeout에 걸리는 중 |
| `hikaricp_connections_acquire_seconds` | 커넥션 얻는 데 걸린 시간 | p99 상승 → 경합 심화 |
| `tomcat_threads_busy_threads` | 바쁜 워커 수 | `= tomcat_threads_config_max(50)` → 스레드 포화 |
| `tomcat_threads_current_threads` | 현재 워커 수 | max 근처 고정 → 포화 임박 |

> ⚠️ `tomcat_threads_*`는 `server.tomcat.mbeanregistry.enabled: true`가 켜져야 노출된다.
> 실측 시점엔 꺼져 있어 해당 지표가 안 나왔다. 위 6장 diff에 포함해 함께 적용한다.

### 7-2. 부하 주고 관찰 (이미 있는 `docs/perf/k6` 활용)

```bash
# 예: DB를 타는 엔드포인트에 동시 50으로 부하
k6 run -e VUS=50 -e DURATION=2m docs/perf/k6/<scenario>.js
# 또는 가볍게:
ab -n 5000 -c 50 https://api.melonnetherapists.com/<endpoint>
```

부하 중 Prometheus/Grafana에서 PromQL로:

```promql
hikaricp_connections_pending{application="backend"}              # >0 지속이면 풀이 병목
rate(hikaricp_connections_timeout_total{application="backend"}[1m])  # >0이면 3초 타임아웃 발생
tomcat_threads_busy_threads{application="backend"}
  / tomcat_threads_config_max_threads{application="backend"}     # 1.0 근처면 스레드 포화
```

### 7-3. 판정 기준

- `pending`이 항상 0, `timeout_total`이 안 늘면 → **풀 10으로 충분**. 더 키울 이유 없음.
- `pending > 0`이 자주 뜨고 `active`가 계속 10 → 풀이 부족. 단, **키우기 전에 RDS 분리/`max_connections` 상향이 먼저**(dev+prod 공유 제약).
- `busy_threads`가 50에 자주 닿음 → 스레드 부족. 단, DB 풀이 안 막혔는지부터 확인(대개 진짜 병목은 풀).

### 7-4. RDS 쪽 커넥션 점유 확인(읽기 전용, 안전)

```sql
SELECT usename, count(*) FROM pg_stat_activity GROUP BY usename ORDER BY 2 DESC;
SELECT count(*) AS total, (SELECT setting::int FROM pg_settings WHERE name='max_connections') AS max
FROM pg_stat_activity;
```

`total`이 81에 근접하면 위험. dev+prod 합산이 60(안전 총량)을 넘는지 주기적으로 본다.

---

## 8. (선택) 기존 설정과의 정합성 점검

이번 튜닝과 직접 관련은 없지만 같이 보면 좋은 항목들:

- **JPA 배치**: `batch_size:50`, `order_inserts:true`는 잘 잡힘. 다만 insert 배치를 PostgreSQL에서 실제로 묶으려면 JDBC URL에 **`reWriteBatchedInserts=true`**를 더하면 효과가 커진다. update까지 묶으려면 `hibernate.order_updates: true`도 함께. (현재 누락 — 선택 개선)
- **Redis Lettuce 풀**: 현재 `timeout`/`connect-timeout`만 설정. Lettuce는 기본이 **단일 멀티플렉싱 커넥션**이라 별도 풀이 보통 불필요하다. 굳이 풀을 쓰려면 `commons-pool2` 의존성 + `spring.data.redis.lettuce.pool.*`가 필요. **현재 구성으로 충분**하니 풀 추가는 권장하지 않음.
- **`open-in-view: false`**: 잘 잡힘. 이게 true면 요청 끝까지 커넥션을 쥐고 있어 위에서 계산한 풀 산정이 다 무너진다. false 유지가 중요.
```
