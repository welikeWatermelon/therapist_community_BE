package com.therapyCommunity_Vol1.backend.global.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "login_failed:";
    private static final int MAX_ATTEMPTS = 10;
    private static final int TTL_SECONDS = 1800;

    public int getFailCount(String email) {
        try {
            String value = stringRedisTemplate.opsForValue().get(KEY_PREFIX + email);
            return value != null ? Integer.parseInt(value) : 0;
        } catch (Exception e) {
            log.warn("Redis getFailCount 실패 email={}", email, e);
            return 0;
        }
    }

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

    public boolean isLocked(String email) {
        return getFailCount(email) >= MAX_ATTEMPTS;
    }

    public void resetFailCount(String email) {
        try {
            stringRedisTemplate.delete(KEY_PREFIX + email);
        } catch (Exception e) {
            log.warn("Redis resetFailCount 실패 email={}", email, e);
        }
    }
}
