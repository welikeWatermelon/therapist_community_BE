# Cache Avalanche vs Cache Stampede — 같은 듯 다른 두 문제, MelonMe는 어떻게 대응했나

## 들어가며

Redis 캐싱을 처음 공부하면 "Cache Avalanche 방지를 위해 TTL에 jitter를 추가한다"는 내용을 자주 접합니다. MelonMe에도 이 방식이 적용되어 있습니다.

그런데 코드 분석을 하다가 중요한 사실을 발견했습니다. **TTL jitter는 Cache Avalanche를 방어하지, Cache Stampede는 방어하지 못합니다.** 두 문제를 같은 것으로 혼동하면 실제로 방어가 안 된 채로 배포하게 됩니다.

이 글에서는 두 문제의 차이를 명확히 짚고, MelonMe 코드 기준으로 어떤 부분이 방어되었고 어떤 부분이 미방어인지 정리합니다.

---

## Cache Avalanche — "여러 키가 동시에 만료"

Cache Avalanche는 **서로 다른 키 다수**가 동시에 만료될 때 발생합니다.

```
상황: 1,000명의 유저 캐시가 모두 TTL 1800초로 설정됨
     → 1800초 뒤 1,000개 키가 동시에 만료
     → 1,000개 요청이 동시에 DB를 직접 조회
     → DB 과부하 → 장애
```

### MelonMe의 방어

`UserCacheService`에 TTL jitter가 적용되어 있습니다.

```java
private static final int BASE_TTL = 1800;
private static final int JITTER_MAX = 300;

// 1800~2099초 사이 랜덤 TTL 적용
int ttl = BASE_TTL + ThreadLocalRandom.current().nextInt(JITTER_MAX);
redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS);
```

jitter를 적용하면 1,000명의 캐시가 1800~2099초 사이에 분산되어 만료됩니다. 초당 평균 만료 수가 `1000 / 300 ≈ 3.3명/초`로 평탄화되어 DB 과부하를 방지합니다.

**→ Cache Avalanche: 방어됨 ✅**

---

## Cache Stampede — "같은 키가 만료되는 순간"

Cache Stampede는 전혀 다른 문제입니다. **동일한 키 하나**가 만료되는 순간, 그 키를 조회하는 동시 요청이 몰릴 때 발생합니다.

```
상황: user:42 키가 만료됨
     → 이 순간 사용자 42의 JWT를 담은 요청 50개가 동시 도착
     → 50개 모두 Redis miss → DB SELECT 50회 발생
     → DB에 같은 행을 50번 읽는 중복 쿼리 폭발
```

TTL jitter는 "서로 다른 키들의 만료 시점"을 분산시킵니다. 하지만 `user:42` 키의 TTL은 하나뿐이고, 그 키가 만료되는 순간에 동시 요청이 몰리는 것은 jitter와 무관하게 발생합니다.

### MelonMe가 특히 취약한 이유

`CustomUserDetailsService.loadUserByUsername()`이 JWT 인증 필터에서 **매 요청마다** 호출됩니다.

```
클라이언트 요청
  → JwtAuthenticationFilter
    → jwtTokenProvider.validateToken() (서명 검증)
    → getUserId()
    → CustomUserDetailsService.loadUserByUsername()  ← 매 요청마다 실행
      → userCacheService.get(userId)
      → [캐시 miss] → userRepository.findById(userId)
```

트래픽이 많은 시간대에 특정 인기 게시글 작성자의 `user:{id}` 키가 만료되면, 해당 게시글 조회 요청들이 동시에 같은 유저 정보를 DB에서 읽습니다. lock이나 singleflight 없이 모든 요청이 DB를 직접 조회합니다.

**→ Cache Stampede: 미방어 ❌ (인지된 한계)**

### 개선 방향

**방법 1: Redis SETNX 분산 락**

캐시 miss 발생 시 `lock:user:{userId}` 키로 SETNX를 시도합니다. 락을 획득한 첫 번째 요청만 DB를 조회하고, 나머지는 락이 풀릴 때까지 짧게 대기한 뒤 캐시를 재조회합니다.

```
캐시 miss 감지
  → SETNX("lock:user:42", TTL 5초) 시도
  → 성공 (첫 번째 요청): DB 조회 → 캐시 저장 → 락 해제
  → 실패 (나머지 요청): 100ms 대기 → 캐시 재조회 (이미 채워짐)
```

단점: Redis 장애 시 락 획득 자체가 불가 → DB 직접 조회로 폴백 필요.

**방법 2: Logical Expiration**

물리 TTL을 충분히 길게 설정하고, 캐시 값에 `logicalExpiry` 필드를 포함합니다. logicalExpiry가 지났을 때 단 하나의 스레드만 갱신을 수행하고, 나머지는 stale 데이터를 반환합니다.

단점: 만료된 데이터를 잠깐 보여줄 수 있음 → 유저 정보처럼 정확성이 중요한 데이터에는 부적합할 수 있음.

현재 MelonMe 규모에서는 트래픽이 아직 Stampede가 체감될 수준이 아니지만, 인기 유저/게시글이 생기거나 부하 테스트 시에 나타날 수 있는 구조적 취약점입니다.

---

## 세 번째 문제: Cache Penetration

Cache Avalanche, Stampede와 함께 자주 언급되는 Cache Penetration도 간단히 정리합니다.

**Cache Penetration**: 존재하지 않는 키로 반복 조회 → 매번 캐시 miss → DB 직접 조회 반복

```
공격 시나리오: userId = 99999999 (존재하지 않는 ID)로 반복 요청
  → 캐시에 없음 → DB 조회 → DB에도 없음 → 캐시에 저장 안 함
  → 다음 요청도 또 DB 조회 → 무한 반복
```

### MelonMe의 방어

DB에 존재하지 않는 userId 조회 시 Redis에 `"NULL"` 센티널 값을 60초 TTL로 저장합니다.

```java
private static final String NULL_VALUE = "NULL";
private static final int NULL_TTL = 60;

// DB 조회 결과 없음 → null 캐싱
if (user == null) {
    redisTemplate.opsForValue().set(KEY_PREFIX + userId, NULL_VALUE, NULL_TTL, TimeUnit.SECONDS);
    return Optional.empty();
}
```

이후 같은 userId 요청이 들어오면 Redis에서 `"NULL"` 을 읽고 DB 조회 없이 즉시 반환합니다.

**→ Cache Penetration: 방어됨 ✅**

---

## Redis 장애 시 보안 문제 — LoginAttemptService

Cache 문제와는 별개로, Redis 의존 구조에서 발생하는 보안 문제도 있습니다.

`LoginAttemptService`는 로그인 실패 횟수를 Redis에 저장해서 10회 실패 시 30분 잠금을 적용합니다. 문제는 Redis가 다운되면 `getFailCount()`가 0을 반환하고, `isLocked()`가 항상 false가 된다는 점입니다. brute-force 방어가 완전히 해제됩니다.

### 해결: 인메모리 Fallback

Redis 예외 발생 시 `ConcurrentHashMap` 기반 인메모리 카운터로 fallback합니다.

```java
// Redis 장애 시 인메모리 fallback
private final ConcurrentHashMap<String, AtomicInteger> localCounts = new ConcurrentHashMap<>();
private final ConcurrentHashMap<String, Long> localExpiry = new ConcurrentHashMap<>();

public int getFailCount(String email) {
    try {
        String value = stringRedisTemplate.opsForValue().get(KEY_PREFIX + email);
        return value != null ? Integer.parseInt(value) : 0;
    } catch (Exception e) {
        return getLocalCount(email);  // Redis 장애 → 인메모리 조회
    }
}
```

**한계**: 인메모리는 서버 인스턴스별 독립 카운트이므로, 다중 인스턴스 환경에서 공격자가 여러 서버에 요청을 분산하면 임계값 도달이 느려집니다. 그러나 완전 무방비(Redis 장애 시 count=0)보다는 훨씬 나은 방어선입니다.

---

## 정리

| 문제 | 발생 조건 | MelonMe 현황 | 방어 방법 |
|------|-----------|--------------|-----------|
| Cache Avalanche | 서로 다른 키 다수 동시 만료 | ✅ 방어됨 | TTL jitter |
| Cache Stampede | 동일 키 만료 시 동시 요청 몰림 | ❌ 미방어 | SETNX 분산 락 / Logical Expiration |
| Cache Penetration | 존재하지 않는 키 반복 조회 | ✅ 방어됨 | null 캐싱 (60초 TTL) |
| Redis 장애 + 보안 | Redis 다운 시 Rate Limit 해제 | ✅ 개선됨 | 인메모리 ConcurrentHashMap fallback |

Cache Avalanche와 Cache Stampede는 이름이 비슷하고 함께 언급되는 경우가 많지만, 발생 조건과 해결 방법이 완전히 다릅니다. TTL jitter 하나로 두 문제를 모두 해결한다고 생각하면 실제로는 Stampede에 무방비 상태가 됩니다. 두 문제를 명확하게 구분하고 각각에 맞는 방어 전략을 적용하는 것이 중요합니다.

---

## 참고

- [토스 기술 블로그: 캐시 문제 해결 가이드 - DB 과부하 방지 실전 팁](https://toss.tech/article/cache-traffic-tip)
- [토스 기술 블로그: 캐시를 적용하기까지의 험난한 길 (TPS 1만 안정적으로 서비스하기)](https://toss.tech/article/34481)
