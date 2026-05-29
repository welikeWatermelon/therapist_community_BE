package com.therapyCommunity_Vol1.backend.post.service.upload;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadRateLimiterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private UploadRateLimiter rateLimiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        rateLimiter = new UploadRateLimiter(redisTemplate);
    }

    @Test
    void checkAndIncrement_firstRequest_passes() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        assertThatCode(() -> rateLimiter.checkAndIncrement(1L)).doesNotThrowAnyException();
    }

    @Test
    void checkAndIncrement_atLimit_passes() {
        when(valueOps.increment(anyString()))
                .thenReturn((long) UploadRateLimiter.PER_MINUTE_LIMIT)
                .thenReturn((long) UploadRateLimiter.DAILY_LIMIT / 2);

        assertThatCode(() -> rateLimiter.checkAndIncrement(1L)).doesNotThrowAnyException();
    }

    @Test
    void checkAndIncrement_perMinuteLimitExceeded_throws() {
        when(valueOps.increment(anyString()))
                .thenReturn((long) UploadRateLimiter.PER_MINUTE_LIMIT + 1);

        assertThatThrownBy(() -> rateLimiter.checkAndIncrement(1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_RATE_LIMIT_EXCEEDED);
    }

    @Test
    void checkAndIncrement_dailyLimitExceeded_throws() {
        when(valueOps.increment(anyString()))
                .thenReturn(1L)  // per-minute: within limit
                .thenReturn((long) UploadRateLimiter.DAILY_LIMIT + 1);  // daily: exceeded

        assertThatThrownBy(() -> rateLimiter.checkAndIncrement(1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_DAILY_LIMIT_EXCEEDED);
    }

    @Test
    void checkAndIncrement_firstRequest_setsExpiry() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        rateLimiter.checkAndIncrement(1L);

        // 분당 키: 90초, 일일 키: 25시간
        verify(redisTemplate).expire(anyString(), eq(90L), eq(TimeUnit.SECONDS));
        verify(redisTemplate).expire(anyString(), eq(25L), eq(TimeUnit.HOURS));
    }

    @Test
    void checkAndIncrement_subsequentRequest_doesNotResetExpiry() {
        when(valueOps.increment(anyString())).thenReturn(5L);

        rateLimiter.checkAndIncrement(1L);

        // increment 결과가 1이 아니면 expire 호출 안 함
        verify(redisTemplate, org.mockito.Mockito.never())
                .expire(anyString(), anyLong(), org.mockito.ArgumentMatchers.any(TimeUnit.class));
    }

    /**
     * Redis 연결 장애 시 rate limit 미적용 (fail-open). 업로드 요청은 통과해야 한다.
     * 폴백 정책은 {@code LoginAttemptService}와 동일(가용성 우선).
     */
    @Test
    void checkAndIncrement_redisConnectionFailure_failsOpen() {
        when(valueOps.increment(anyString()))
                .thenThrow(new RedisConnectionFailureException("simulated"));

        assertThatCode(() -> rateLimiter.checkAndIncrement(1L))
                .doesNotThrowAnyException();
    }

    /**
     * 첫 Redis 호출에서 예외가 던져지면 단일 try-catch로 빠져나가므로
     * daily 카운터 호출은 아예 발생하지 않는다.
     */
    @Test
    void checkAndIncrement_redisTimeout_doesNotCallDailyCounter() {
        when(valueOps.increment(anyString()))
                .thenThrow(new QueryTimeoutException("simulated"));

        rateLimiter.checkAndIncrement(1L);

        verify(valueOps, times(1)).increment(anyString());
    }
}
