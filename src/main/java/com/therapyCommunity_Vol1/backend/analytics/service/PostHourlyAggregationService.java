package com.therapyCommunity_Vol1.backend.analytics.service;

import com.therapyCommunity_Vol1.backend.analytics.domain.AggregationProgress;
import com.therapyCommunity_Vol1.backend.analytics.repository.AggregationProgressRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * user_events를 1시간 윈도우로 post_hourly_stats에 롤업하는 배치.
 *
 * 멱등성: 각 hour를 "DELETE → INSERT"로 전체 재계산. 같은 hour를 몇 번 실행해도 결과 동일.
 * 재실행 안전: aggregation_progress 커서를 PESSIMISTIC_WRITE로 잠가 중복 실행 방지.
 * 지연 버퍼: now() 기준 1시간 전까지만 집계 → 이벤트 지연 도착을 수용.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostHourlyAggregationService {

    static final String JOB_NAME = "post_hourly_stats";

    /** 지연 도착 이벤트 수용용 안전 마진. 현재 시각 기준 이만큼 전의 hour까지만 집계. */
    private static final Duration LATENCY_BUFFER = Duration.ofMinutes(60);

    /** 배치 1회에 한 번에 처리할 최대 hour 수 (back-pressure + 트랜잭션 길이 제한). */
    private static final int MAX_HOURS_PER_RUN = 24;

    private final AggregationProgressRepository progressRepository;
    private final EntityManager entityManager;

    /**
     * 커서에서 "지금 - LATENCY_BUFFER의 hour 시작" 사이에 있는 미처리 hour를 모두 집계.
     * 처리할 hour가 없으면 조용히 return.
     *
     * @return 실제 집계된 hour 수
     */
    @Transactional
    public int aggregatePendingHours() {
        AggregationProgress progress = progressRepository.findByJobName(JOB_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "aggregation_progress 레코드가 없습니다: " + JOB_NAME));

        LocalDateTime cursor = progress.getLastWindowEnd();
        LocalDateTime ceiling = LocalDateTime.now()
                .minus(LATENCY_BUFFER)
                .truncatedTo(ChronoUnit.HOURS);

        if (!cursor.isBefore(ceiling)) {
            return 0;
        }

        int processed = 0;
        LocalDateTime windowStart = cursor;
        while (windowStart.isBefore(ceiling) && processed < MAX_HOURS_PER_RUN) {
            LocalDateTime windowEnd = windowStart.plusHours(1);
            recomputeHour(windowStart, windowEnd);
            processed++;
            windowStart = windowEnd;
        }

        progress.advance(windowStart);

        log.info("post_hourly_stats 집계 완료: {} hour 처리, 커서 {} → {}",
                processed, cursor, windowStart);
        return processed;
    }

    /**
     * 단일 hour 윈도우를 재계산. 기존 통계 삭제 후 raw 이벤트로부터 완전 재작성.
     * FILTER (WHERE ...) 절로 10종 카운트를 1회 쿼리에 집계.
     */
    private void recomputeHour(LocalDateTime windowStart, LocalDateTime windowEnd) {
        Query delete = entityManager.createNativeQuery(
                "DELETE FROM post_hourly_stats WHERE hour = :h");
        delete.setParameter("h", windowStart);
        delete.executeUpdate();

        Query insert = entityManager.createNativeQuery("""
                INSERT INTO post_hourly_stats (
                    post_id, hour,
                    view_cnt, unique_viewers,
                    reaction_like_cnt, reaction_curious_cnt, reaction_useful_cnt,
                    scrap_cnt, comment_cnt,
                    download_cnt, unique_downloaders
                )
                SELECT
                    target_id AS post_id,
                    date_trunc('hour', occurred_at) AS hour,
                    COUNT(*) FILTER (WHERE event_type = 'POST_VIEW'),
                    COUNT(DISTINCT user_id) FILTER (WHERE event_type = 'POST_VIEW'),
                    COUNT(*) FILTER (WHERE event_type = 'POST_REACT' AND metadata->>'reactionType' = 'LIKE'),
                    COUNT(*) FILTER (WHERE event_type = 'POST_REACT' AND metadata->>'reactionType' = 'CURIOUS'),
                    COUNT(*) FILTER (WHERE event_type = 'POST_REACT' AND metadata->>'reactionType' = 'USEFUL'),
                    COUNT(*) FILTER (WHERE event_type = 'POST_SCRAP'),
                    COUNT(*) FILTER (WHERE event_type = 'COMMENT_CREATE'),
                    COUNT(*) FILTER (WHERE event_type = 'ATTACHMENT_DOWNLOAD'),
                    COUNT(DISTINCT user_id) FILTER (WHERE event_type = 'ATTACHMENT_DOWNLOAD')
                FROM user_events
                WHERE target_type = 'POST'
                  AND occurred_at >= :windowStart
                  AND occurred_at <  :windowEnd
                GROUP BY target_id, date_trunc('hour', occurred_at)
                """);
        insert.setParameter("windowStart", windowStart);
        insert.setParameter("windowEnd", windowEnd);
        int inserted = insert.executeUpdate();

        log.debug("  └ hour {} → {} posts 집계", windowStart, inserted);
    }
}
