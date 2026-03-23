package com.therapyCommunity_Vol1.backend.notification.service;

import com.therapyCommunity_Vol1.backend.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnreadCountCacheService {

    private static final String KEY_PREFIX = "notification:unread:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationRepository notificationRepository;

    /**
     * 안 읽은 알림 개수 조회
     * Redis에 없으면 DB에서 조회 후 캐시
     */
    public long getUnreadCount(Long userId) {
        String key = buildKey(userId);
        Object cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            return Long.parseLong(cached.toString());
        }

        // Cache miss - DB에서 조회 후 캐시
        long count = notificationRepository.countUnreadByRecipientId(userId);
        redisTemplate.opsForValue().set(key, String.valueOf(count));
        log.debug("Cache miss for user {}, loaded from DB: {}", userId, count);
        return count;
    }

    /**
     * 안 읽은 개수 증가 (+1)
     */
    public void increment(Long userId) {
        String key = buildKey(userId);
        Object cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            redisTemplate.opsForValue().increment(key);
        } else {
            // 캐시가 없으면 DB 조회 후 +1 하여 저장
            long count = notificationRepository.countUnreadByRecipientId(userId);
            redisTemplate.opsForValue().set(key, String.valueOf(count + 1));
        }
        log.debug("Incremented unread count for user {}", userId);
    }

    /**
     * 안 읽은 개수 감소 (-1)
     */
    public void decrement(Long userId) {
        String key = buildKey(userId);
        Object cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            long currentCount = Long.parseLong(cached.toString());
            if (currentCount > 0) {
                redisTemplate.opsForValue().decrement(key);
            }
        } else {
            // 캐시가 없으면 DB 조회 후 -1 하여 저장 (최소 0)
            long count = notificationRepository.countUnreadByRecipientId(userId);
            long newCount = Math.max(0, count - 1);
            redisTemplate.opsForValue().set(key, String.valueOf(newCount));
        }
        log.debug("Decremented unread count for user {}", userId);
    }

    /**
     * 안 읽은 개수를 0으로 리셋
     */
    public void reset(Long userId) {
        String key = buildKey(userId);
        redisTemplate.opsForValue().set(key, "0");
        log.debug("Reset unread count for user {}", userId);
    }

    private String buildKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}
