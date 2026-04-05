package com.therapyCommunity_Vol1.backend.global.cache;

import com.therapyCommunity_Vol1.backend.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * User 정보 Redis 캐시 서비스.
 *
 * 키 형식: "user:{userId}"
 * 전략:
 *  - Cache-Aside: 조회 시 캐시 확인 → miss면 DB 조회 후 캐시 저장
 *  - TTL jitter: 1800 + rand(0~300)초 — 동시 만료로 인한 Cache Avalanche 방지
 *  - Null 캐싱: 존재하지 않는 userId에 "NULL" 저장 (TTL 60초) — Cache Penetration 방지
 *  - 명시적 Eviction: 프로필 수정, 탈퇴, 역할 변경 시 캐시 즉시 삭제
 *
 * Redis 장애 시 모든 메서드가 fallback (예외 무시, DB 직접 조회로 전환)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "user:";
    private static final String NULL_VALUE = "NULL";   // Null 캐싱용 센티널 값
    private static final int BASE_TTL = 1800;          // 기본 TTL 30분
    private static final int JITTER_MAX = 300;         // 랜덤 jitter 최대 5분
    private static final int NULL_TTL = 60;            // Null 캐시 TTL 1분

    /**
     * 캐시에서 User 조회.
     * @return 캐시 hit → User, null 캐시 hit 또는 miss → empty
     */
    public Optional<User> get(Long userId) {
        try {
            Object value = redisTemplate.opsForValue().get(KEY_PREFIX + userId);

            if (value == null) {
                return Optional.empty();  // 캐시 miss
            }

            if (NULL_VALUE.equals(value)) {
                return Optional.empty();  // null 캐시 hit — isNullCached()로 별도 판별
            }

            if (value instanceof CachedUser cachedUser) {
                return Optional.of(cachedUser.toEntity());
            }

            return Optional.empty();
        } catch (Exception e) {
            log.warn("Redis get 실패 userId={}", userId, e);
            return Optional.empty();  // Redis 장애 시 캐시 miss 처리 → DB fallback
        }
    }

    /**
     * 해당 userId가 "존재하지 않음"으로 캐싱되었는지 확인.
     * Cache Penetration 방어: 존재하지 않는 userId로 반복 요청 시 DB 조회 차단.
     */
    public boolean isNullCached(Long userId) {
        try {
            Object value = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
            return NULL_VALUE.equals(value);
        } catch (Exception e) {
            return false;  // Redis 장애 시 DB 조회 허용
        }
    }

    /**
     * User 캐시 저장.
     * TTL = BASE_TTL + random jitter → 동일 시점에 대량의 캐시가 동시 만료되는 것을 방지.
     */
    public void put(Long userId, User user) {
        try {
            long ttl = BASE_TTL + ThreadLocalRandom.current().nextInt(JITTER_MAX);
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + userId,
                    CachedUser.from(user),
                    ttl,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("Redis put 실패 userId={}", userId, e);
        }
    }

    /**
     * "존재하지 않는 userId" 캐싱 (Cache Penetration 방지).
     * 짧은 TTL(60초)로 저장하여 실제 가입 시 빠르게 만료.
     */
    public void putNull(Long userId) {
        try {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + userId,
                    NULL_VALUE,
                    NULL_TTL,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("Redis putNull 실패 userId={}", userId, e);
        }
    }

    /**
     * 명시적 캐시 무효화 (Eviction).
     * 호출 시점: 프로필 수정, 탈퇴, 치료사 인증 신청/승인/거절 (역할 변경)
     */
    public void evict(Long userId) {
        try {
            redisTemplate.delete(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Redis evict 실패 userId={}", userId, e);
        }
    }
}
