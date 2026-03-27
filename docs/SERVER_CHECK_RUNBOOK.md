# 서버 점검 매뉴얼

## 목적

운영 서버가 실제로 정상 동작 중인지 빠르게 확인하고, 장애와 단순 요청 오류를 구분하기 위한 수동 점검 절차를 정리한다.

이 문서는 2026년 3월 25일에 실제 EC2 서버를 점검하며 사용한 명령과 해석 기준을 기준으로 작성했다.

---

## 대상 서버

- SSH 접속

```bash
ssh -i /Users/tom/dev/buildersMvp/melonne-key.pem ubuntu@43.203.40.3
```

- 서버 정보
  - EC2 Public IP: `43.203.40.3`
  - SSH User: `ubuntu`
  - Backend service: `backend`
  - Reverse proxy: `nginx`
  - Backend internal port: `8080`
  - Public HTTPS domain: `https://api.melonnetherapists.com`

---

## 1분 점검 순서

아래 5개가 모두 정상이라면 서버는 대체로 정상으로 본다.

1. `backend.service` 가 `active (running)` 인지 확인
2. `nginx.service` 가 `active (running)` 인지 확인
3. `java` 가 `:8080` 에서 LISTEN 중인지 확인
4. `http://127.0.0.1:8080/v3/api-docs` 가 `200` 인지 확인
5. `https://api.melonnetherapists.com/v3/api-docs` 가 `200` 인지 확인

---

## 점검 절차 상세

### 1. 프로세스와 포트 확인

명령:

```bash
ssh -i /Users/tom/dev/buildersMvp/melonne-key.pem ubuntu@43.203.40.3 \
'hostname; uptime; ss -ltnp; ps -ef | grep -E "java|gradle|nginx|backend" | grep -v grep'
```

무엇을 보는가:

- `ss -ltnp` 에서 `:8080`, `:80`, `:443` LISTEN 여부
- `ps -ef` 에서 실제 실행 중인 `java`, `nginx` 프로세스
- Java 실행 커맨드에서 jar 이름과 active profile

정상 예시:

```text
LISTEN *:8080 users:(("java",pid=690594,...))
LISTEN 0.0.0.0:80
LISTEN 0.0.0.0:443
ubuntu 690594 ... java -jar /home/ubuntu/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

해석:

- `:8080` 이 열려 있으면 Spring Boot 프로세스가 포트를 잡고 있다.
- `:80`, `:443` 이 열려 있으면 nginx 가 외부 요청을 받을 준비가 된 상태다.
- `--spring.profiles.active=prod` 를 보면 운영 프로필로 실행 중임을 알 수 있다.

---

### 2. systemd 서비스 상태 확인

명령:

```bash
sudo systemctl status backend --no-pager -l | sed -n '1,25p'
sudo systemctl status nginx --no-pager -l | sed -n '1,25p'
```

무엇을 보는가:

- `Loaded`
- `Active`
- `Main PID`
- 최근 로그 몇 줄

정상 기준:

```text
Active: active (running)
```

해석:

- `active (running)` 이면 systemd 기준으로 서비스가 살아 있다.
- `failed`, `activating`, `deactivating`, 재시작 반복 흔적이 보이면 추가 조사 필요.

---

### 3. 백엔드 내부 응답 확인

명령:

```bash
curl -sS -i http://127.0.0.1:8080/v3/api-docs | sed -n '1,20p'
```

무엇을 보는가:

- HTTP status
- `Content-Type`

정상 기준:

```text
HTTP/1.1 200
Content-Type: application/json
```

해석:

- 포트만 열려 있는 게 아니라, Spring Boot 애플리케이션이 실제로 HTTP 요청을 처리하고 있다는 뜻이다.
- 여기서 실패하면 앱 초기화 실패, 포트 변경, 방화벽, 프로세스 비정상 등 내부 문제 가능성이 크다.

---

### 4. 공개 HTTPS 응답 확인

명령:

```bash
curl -sS -i https://api.melonnetherapists.com/v3/api-docs | sed -n '1,20p'
```

무엇을 보는가:

- HTTP status
- 응답 헤더의 `Server`
- `Strict-Transport-Security` 같은 HTTPS 헤더

정상 기준:

```text
HTTP/1.1 200
Server: nginx/1.24.0 (Ubuntu)
```

해석:

- 외부 도메인에서 nginx 를 거쳐 백엔드까지 정상 연결된 상태다.
- 내부 `8080` 은 되는데 공개 HTTPS 만 안 되면 nginx 설정, 인증서, DNS, 보안그룹 쪽 문제를 의심한다.

주의:

- 실제 운영 도메인은 `api.melonnetherapists.com` 이다.
- 잘못된 도메인으로 체크하면 DNS 실패와 구분이 필요하다.

---

### 5. nginx 설정 검증

명령:

```bash
sudo nginx -t
```

무엇을 보는가:

- 문법 에러 여부

정상 기준:

```text
nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
nginx: configuration file /etc/nginx/nginx.conf test is successful
```

해석:

- 현재 반영된 nginx 설정 파일이 최소한 문법적으로는 정상이다.
- `sudo` 없이 실행하면 인증서 파일 권한 때문에 오탐이 날 수 있다.

---

### 6. 최근 로그 확인

명령:

```bash
journalctl -u backend -n 100 --no-pager
```

최근 구간만 볼 때:

```bash
journalctl -u backend --since "10 min ago" --no-pager
```

에러/경고만 볼 때:

```bash
journalctl -u backend --since "10 min ago" --no-pager | grep -E "ERROR|WARN|Exception|Caused by"
```

무엇을 보는가:

- `ERROR`, `WARN`
- 예외 클래스명
- path 정보
- 같은 예외가 반복되는지 여부

해석:

- `MethodArgumentNotValidException`, `BadRequest` 는 보통 클라이언트 요청 값 문제다.
- `Unhandled Exception`, DB connection 에러, startup failure, bind failure 는 서버 이상 가능성이 높다.

---

## 점검 결과 해석표

### 경우 1

- `backend active`
- `8080 -> 200`
- `https -> 200`

판단:

- 서버 정상

### 경우 2

- `backend active`
- `8080 -> 200`
- `https -> 실패`

판단:

- nginx, SSL, DNS, 보안그룹 문제 가능성

### 경우 3

- `backend active`
- `8080 -> 실패`

판단:

- 앱은 떠 있어 보여도 내부 초기화 실패, 잘못된 포트, 비정상 응답 가능성

### 경우 4

- `backend failed`

판단:

- 애플리케이션 프로세스 자체 문제
- `journalctl -u backend -n 200 --no-pager` 로 원인 확인

---

## 2026-03-25 실제 점검 사례

### 당시 확인한 결론

- `backend.service`: 정상 실행 중
- `nginx.service`: 정상 실행 중
- 내부 백엔드 `8080`: 정상 응답
- 공개 HTTPS 도메인: 정상 응답
- 최근 로그의 예외는 서버 장애가 아니라 요청 검증 실패

### 관련 로그 시각

검증 실패 요청 시각:

- UTC: `2026-03-25 13:36:57.620`
- KST: `2026-03-25 22:36:57.620`

요청 경로:

- `POST /api/v1/posts`

관련 로그 확인 명령:

```bash
journalctl -u backend --since "2026-03-25 13:36:50" --until "2026-03-25 13:37:05" --no-pager
```

핵심 로그:

```text
BadRequest: path=/api/v1/posts
MethodArgumentNotValidException
field 'postType': rejected value [null]
field 'ageGroup': rejected value [null]
```

해석:

- 요청 본문에 `postType`, `ageGroup` 이 없거나 `null` 로 들어왔다.
- 서버가 죽은 것이 아니라, Spring Validation 이 정상적으로 400 Bad Request 를 반환한 것이다.

---

## 왜 검증 실패가 났는가

`POST /api/v1/posts` 는 `@Valid @RequestBody CreateTherapyPostRequest` 를 받는다.

관련 코드:

- [PostController.java](/Users/tom/dev/buildersMvp/backend/src/main/java/com/therapyCommunity_Vol1/backend/post/controller/PostController.java)
- [CreateTherapyPostRequest.java](/Users/tom/dev/buildersMvp/backend/src/main/java/com/therapyCommunity_Vol1/backend/post/dto/CreateTherapyPostRequest.java)

필수 필드:

```java
@NotBlank(message = "제목은 필수입니다.")
private String title;

@NotBlank(message = "내용은 필수입니다")
private String content;

@NotNull(message = "게시글 타입은 필수입니다.")
private PostType postType;

@NotNull(message = "치료 종류는 필수입니다.")
private TherapyArea therapyArea;

@NotNull(message = "연령대는 필수입니다.")
private AgeGroup ageGroup;
```

이번 실패 원인:

- `postType = null`
- `ageGroup = null`

즉, 프론트 또는 API 호출 측에서 필수 필드를 누락했다.

---

## 게시글 생성 요청 예시

### 잘못된 예시

```json
{
  "title": "제목",
  "content": "내용",
  "therapyArea": "SPEECH"
}
```

문제:

- `postType` 없음
- `ageGroup` 없음

### 정상 예시

```json
{
  "title": "제목",
  "content": "내용",
  "postType": "COMMUNITY",
  "therapyArea": "SPEECH",
  "ageGroup": "AGE_6_12"
}
```

---

## 자주 쓰는 명령어 모음

```bash
# SSH 접속
ssh -i /Users/tom/dev/buildersMvp/melonne-key.pem ubuntu@43.203.40.3

# 서비스 상태
sudo systemctl status backend --no-pager -l | sed -n '1,25p'
sudo systemctl status nginx --no-pager -l | sed -n '1,25p'

# 포트 확인
ss -ltnp | grep -E ':8080|:80|:443'

# 내부 앱 체크
curl -sS -i http://127.0.0.1:8080/v3/api-docs | sed -n '1,20p'

# 공개 HTTPS 체크
curl -sS -i https://api.melonnetherapists.com/v3/api-docs | sed -n '1,20p'

# nginx 설정 테스트
sudo nginx -t

# 최근 로그
journalctl -u backend -n 100 --no-pager

# 최근 10분 에러/경고
journalctl -u backend --since "10 min ago" --no-pager | grep -E "ERROR|WARN|Exception|Caused by"
```

---

## 점검 후 메모 남길 때 추천 포맷

```text
[서버 점검 결과]
- 점검 시각(KST):
- backend.service:
- nginx.service:
- 8080 /v3/api-docs:
- public https /v3/api-docs:
- 최근 에러:
- 최종 판단:
```
