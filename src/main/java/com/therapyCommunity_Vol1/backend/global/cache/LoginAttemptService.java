package com.therapyCommunity_Vol1.backend.global.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 로그인 실패 횟수 Redis 관리 (Rate Limiting / Brute-force 방지).
 *
 * 키 형식: "login_failed:{email}"
 * 정책:
 *  - 10회 실패 → 30분(1800초) 잠금
 *  - 첫 실패 시 TTL 설정, 이후 실패마다 INCR (TTL 유지)
 *  - 로그인 성공 시 카운터 즉시 삭제
 *  - 이메일 존재 여부와 무관하게 카운트 증가 (이메일 열거 공격 방어)
 *
 * Redis 장애 시 잠금을 적용하지 않음 (가용성 우선)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "login_failed:";
    private static final int MAX_ATTEMPTS = 10;    // 잠금까지 허용 실패 횟수
    private static final int TTL_SECONDS = 1800;   // 잠금 지속 시간 30분

    /** 현재 실패 횟수 조회. Redis 장애 시 0 반환 (잠금 미적용). */
    public int getFailCount(String email) {
        try {
            String value = stringRedisTemplate.opsForValue().get(KEY_PREFIX + email);
            return value != null ? Integer.parseInt(value) : 0;
        } catch (Exception e) {
            log.warn("Redis getFailCount 실패 email={}", email, e);
            return 0;
        }
    }

    /**
     * 실패 횟수 1 증가.
     * 최초 실패 시(count == 1) TTL 설정. 이후 실패에서는 TTL 갱신 안 함.
     */
    public void recordFailure(String email) {
        try {
            String key = KEY_PREFIX + email;
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, TTL_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("Redis recordFailure 실패 email={}", email, e);
        }
    }

    /** 잠금 여부 확인. MAX_ATTEMPTS 이상이면 잠금 상태. */
    public boolean isLocked(String email) {
        return getFailCount(email) >= MAX_ATTEMPTS;
    }

    /** 로그인 성공 시 카운터 즉시 삭제. */
    public void resetFailCount(String email) {
        try {
            stringRedisTemplate.delete(KEY_PREFIX + email);
        } catch (Exception e) {
            log.warn("Redis resetFailCount 실패 email={}", email, e);
        }
    }
}
