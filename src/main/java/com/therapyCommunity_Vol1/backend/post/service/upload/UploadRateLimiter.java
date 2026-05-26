package com.therapyCommunity_Vol1.backend.post.service.upload;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class UploadRateLimiter {

    static final int PER_MINUTE_LIMIT = 10;
    static final int DAILY_LIMIT = 100;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 호출 시 해당 사용자의 분당/일일 카운터를 원자적으로 증가시키고 한도 초과 시 예외.
     * 한도 초과 판단은 증가 이후 기준(INCR 후 비교)이므로 정확히 한도+1번째 요청부터 거절.
     */
    public void checkAndIncrement(Long userId) {
        long epochMinute = Instant.now().truncatedTo(ChronoUnit.MINUTES).getEpochSecond();
        String minuteKey = "upload:rate:" + userId + ":" + epochMinute;
        String dailyKey = "upload:daily:" + userId + ":" + LocalDate.now(ZoneId.of("Asia/Seoul"));

        Long minuteCount = stringRedisTemplate.opsForValue().increment(minuteKey);
        if (minuteCount != null && minuteCount == 1L) {
            stringRedisTemplate.expire(minuteKey, 90, TimeUnit.SECONDS);
        }
        if (minuteCount != null && minuteCount > PER_MINUTE_LIMIT) {
            throw new CustomException(ErrorCode.UPLOAD_RATE_LIMIT_EXCEEDED);
        }

        Long dailyCount = stringRedisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1L) {
            stringRedisTemplate.expire(dailyKey, 25, TimeUnit.HOURS);
        }
        if (dailyCount != null && dailyCount > DAILY_LIMIT) {
            throw new CustomException(ErrorCode.UPLOAD_DAILY_LIMIT_EXCEEDED);
        }
    }
}
