package com.therapyCommunity_Vol1.backend.analytics.scheduler;

import com.therapyCommunity_Vol1.backend.analytics.service.PostHourlyAggregationService;
import com.therapyCommunity_Vol1.backend.analytics.service.TherapistExpertiseAggregationService;
import com.therapyCommunity_Vol1.backend.analytics.service.UserEventPartitionService;
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
    private final TherapistExpertiseAggregationService therapistExpertiseAggregationService;
    private final UserEventPartitionService userEventPartitionService;

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

    /**
     * 기본: 매일 00:15 실행.
     * post_hourly_stats보다 늦게 트리거해 전날 마지막 hour 집계가 끝난 뒤 돌도록.
     */
    @Scheduled(cron = "${analytics.therapist-expertise.cron:0 15 0 * * *}")
    public void runTherapistExpertiseAggregation() {
        try {
            int processed = therapistExpertiseAggregationService.aggregatePendingDays();
            if (processed > 0) {
                log.info("AnalyticsScheduler: therapist_expertise_daily {} days 집계", processed);
            }
        } catch (Exception e) {
            log.error("AnalyticsScheduler: therapist_expertise_daily 집계 실패", e);
        }
    }

    /**
     * 기본: 매월 1일 01:00 실행.
     * user_events 월별 파티션이 도래 전에 미리 생성되도록.
     * 파티션 누락 시 INSERT가 실패해 이벤트 유실로 직결되므로, 본 배치는 운영 안정성에 직접 영향.
     * 한 번 누락돼도 LOOKAHEAD_MONTHS 만큼 buffer 가짐.
     */
    @Scheduled(cron = "${analytics.user-event-partition.cron:0 0 1 1 * *}")
    public void runUserEventPartitionMaintenance() {
        try {
            int created = userEventPartitionService.ensurePartitions();
            log.info("AnalyticsScheduler: user_events 파티션 보장 완료 (신규 {}개)", created);
        } catch (Exception e) {
            log.error("AnalyticsScheduler: user_events 파티션 생성 실패", e);
        }
    }
}
