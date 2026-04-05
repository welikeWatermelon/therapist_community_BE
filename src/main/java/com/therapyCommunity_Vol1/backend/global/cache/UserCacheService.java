package com.therapyCommunity_Vol1.backend.global.cache;

import com.therapyCommunity_Vol1.backend.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "user:";
    private static final String NULL_VALUE = "NULL";
    private static final int BASE_TTL = 1800;
    private static final int JITTER_MAX = 300;
    private static final int NULL_TTL = 60;

    public Optional<User> get(Long userId) {
        try {
            Object value = redisTemplate.opsForValue().get(KEY_PREFIX + userId);

            if (value == null) {
                return Optional.empty();
            }

            if (NULL_VALUE.equals(value)) {
                return Optional.empty();
            }

            if (value instanceof CachedUser cachedUser) {
                return Optional.of(cachedUser.toEntity());
            }

            return Optional.empty();
        } catch (Exception e) {
            log.warn("Redis get 실패 userId={}", userId, e);
            return Optional.empty();
        }
    }

    public boolean isNullCached(Long userId) {
        try {
            Object value = redisTemplate.opsForValue().get(KEY_PREFIX + userId);
            return NULL_VALUE.equals(value);
        } catch (Exception e) {
            return false;
        }
    }

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

    public void evict(Long userId) {
        try {
            redisTemplate.delete(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Redis evict 실패 userId={}", userId, e);
        }
    }
}
