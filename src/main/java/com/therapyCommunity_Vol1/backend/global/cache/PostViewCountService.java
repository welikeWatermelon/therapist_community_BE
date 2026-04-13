package com.therapyCommunity_Vol1.backend.global.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static java.lang.Boolean.TRUE;

/**
 * 게시글 조회수 중복 증가 방지 (Redis SETNX + TTL).
 *
 * 키 형식: "post_view:{postId}:{userId}"
 * 정책:
 *  - 같은 유저가 같은 글을 30분 내 재조회 시 조회수 증가 안 함
 *  - setIfAbsent 한 번의 호출로 조회 + 기록 원자적 처리
 *
 * Redis 장애 시 조회수 증가 허용 (가용성 우선)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostViewCountService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "post_view:";
    private static final int TTL_SECONDS = 1800; // 30분

    /**
     * 해당 유저가 해당 게시글을 최근 30분 내 조회한 적이 없으면 true 반환.
     * Redis 장애 시 true 반환 (조회수 증가 허용).
     */
    public boolean isFirstView(Long postId, Long userId) {
        try {
            String cacheEntry = KEY_PREFIX + postId + ":" + userId;
            Boolean wasAbsent = stringRedisTemplate.opsForValue()
                    .setIfAbsent(cacheEntry, "1", TTL_SECONDS, TimeUnit.SECONDS);
            return TRUE.equals(wasAbsent);
            // wasAbsent는 Wrapper 타입이라 null이 올 수 있음. 따라서 방어적으로 코드 작성한 것
            // null이 올 가능성이 없다면 wasAbsent만 와도 됨.
        } catch (Exception e) {
            log.warn("Redis isFirstView 실패 postId={}, userId={}", postId, userId, e);
            return true;
        }
    }
}
