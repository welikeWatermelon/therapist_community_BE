package com.therapyCommunity_Vol1.backend.analytics.scheduler;

import com.therapyCommunity_Vol1.backend.analytics.service.PostHourlyAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Analytics 집계 배치 트리거.
 * cron은 설정으로 오버라이드 가능 (local에서 짧게 돌리고 싶을 때 application-local에서 변경).
 * 실패는 로그만 남기고 다음 주기에 자연 복구 (LATENCY_BUFFER 덕분에 몇 주기 밀려도 재처리 가능).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsScheduler {

    private final PostHourlyAggregationService postHourlyAggregationService;

    /** 기본: 매 시 정각 5분에 실행 (직전 hour의 이벤트가 대부분 도착했을 시점). */
    @Scheduled(cron = "${analytics.post-hourly.cron:0 5 * * * *}")
    public void runPostHourlyAggregation() {
        try {
            int processed = postHourlyAggregationService.aggregatePendingHours();
            if (processed > 0) {
                log.info("AnalyticsScheduler: post_hourly_stats {} hour 집계", processed);
            }
        } catch (Exception e) {
            log.error("AnalyticsScheduler: post_hourly_stats 집계 실패", e);
        }
    }
}
