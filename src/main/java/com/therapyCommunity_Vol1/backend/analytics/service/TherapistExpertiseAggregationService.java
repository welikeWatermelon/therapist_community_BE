package com.therapyCommunity_Vol1.backend.analytics.service;

import com.therapyCommunity_Vol1.backend.analytics.domain.AggregationProgress;
import com.therapyCommunity_Vol1.backend.analytics.repository.AggregationProgressRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 치료사 전문성 점수 일간 집계.
 *
 * 입력: post_hourly_stats + therapy_posts + users(THERAPIST만)
 * 처리: 직근 WINDOW_DAYS일 활동 → log 변환 → z-score → Laplace smoothing → 가중합 → 백분위
 * 출력: therapist_expertise_daily (as_of_date별 스냅샷)
 *
 * 멱등성: (user_id, as_of_date) PK에 대해 DELETE → INSERT.
 * 멀티 인스턴스 안전: aggregation_progress 락으로 직렬화.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TherapistExpertiseAggregationService {

    static final String JOB_NAME = "therapist_expertise_daily";
    private static final int WINDOW_DAYS = 30;
    private static final int MAX_DAYS_PER_RUN = 7;

    // 가중치 — 도메인 휴리스틱 초기값. Phase 4의 A/B 결과로 튜닝 예정.
    private static final double W_POSTS         = 1.0;   // 생산성
    private static final double W_USEFUL        = 2.0;   // 품질 (최상위)
    private static final double W_CURIOUS       = 0.5;   // engagement 유발
    private static final double W_DOWNLOADS     = 1.5;   // 자료 활용도
    private static final double W_USEFUL_RATIO  = 1.0;   // Laplace smoothed 품질 비율

    // Laplace prior: Beta(1, 9). 총 반응 0개 유저는 useful_ratio = 1/10 = 0.1에서 출발.
    private static final double LAPLACE_ALPHA = 1.0;
    private static final double LAPLACE_BETA  = 9.0;

    private final AggregationProgressRepository progressRepository;
    private final EntityManager entityManager;

    /**
     * 커서에서 어제까지 미처리된 as_of_date를 순회 처리.
     * @return 처리된 날짜 수
     */
    @Transactional
    public int aggregatePendingDays() {
        AggregationProgress progress = progressRepository.findByJobName(JOB_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "aggregation_progress 레코드가 없습니다: " + JOB_NAME));

        LocalDate cursor = progress.getLastWindowEnd().toLocalDate();
        LocalDate ceiling = LocalDate.now().minusDays(1); // 어제까지만 (오늘은 아직 진행중)

        if (!cursor.isBefore(ceiling)) {
            return 0;
        }

        int processed = 0;
        LocalDate target = cursor.plusDays(1);
        while (!target.isAfter(ceiling) && processed < MAX_DAYS_PER_RUN) {
            recomputeDate(target);
            processed++;
            target = target.plusDays(1);
        }

        // target은 "다음에 처리할 날짜"가 되어있음. 커서는 "마지막으로 처리 완료한 날짜의 00:00".
        LocalDate lastProcessed = target.minusDays(1);
        progress.advance(LocalDateTime.of(lastProcessed, LocalTime.MIDNIGHT));

        log.info("therapist_expertise_daily 집계 완료: {} days 처리, 커서 {} → {}",
                processed, cursor, lastProcessed);
        return processed;
    }

    /**
     * 특정 as_of_date에 대해 전 치료사의 전문성 점수를 재계산.
     * CTE 체인으로 raw → log → z-score → weighted sum → percentile을 1쿼리에 산출.
     */
    private void recomputeDate(LocalDate asOfDate) {
        LocalDateTime windowStart = asOfDate.minusDays(WINDOW_DAYS - 1L).atStartOfDay();
        LocalDateTime windowEnd = asOfDate.plusDays(1).atStartOfDay();

        entityManager.createNativeQuery(
                "DELETE FROM therapist_expertise_daily WHERE as_of_date = :d")
                .setParameter("d", asOfDate)
                .executeUpdate();

        Query insert = entityManager.createNativeQuery("""
                WITH
                -- 1단계: 치료사별 원시 카운트 (윈도우 내 활동)
                raw_counts AS (
                    SELECT
                        u.id AS user_id,
                        COUNT(DISTINCT p.id) FILTER (
                            WHERE p.deleted_at IS NULL AND p.created_at >= :windowStart AND p.created_at < :windowEnd
                        ) AS posts_count,
                        COALESCE(SUM(phs.reaction_useful_cnt), 0)                            AS useful_received,
                        COALESCE(SUM(phs.reaction_curious_cnt), 0)                           AS curious_received,
                        COALESCE(SUM(phs.download_cnt), 0)                                   AS downloads_received,
                        COALESCE(SUM(phs.reaction_like_cnt
                                   + phs.reaction_curious_cnt
                                   + phs.reaction_useful_cnt), 0)                            AS total_reactions_received
                    FROM users u
                    LEFT JOIN therapy_posts p
                           ON p.author_id = u.id
                          AND p.deleted_at IS NULL
                    LEFT JOIN post_hourly_stats phs
                           ON phs.post_id = p.id
                          AND phs.hour >= :windowStart
                          AND phs.hour <  :windowEnd
                    WHERE u.role = 'THERAPIST'
                    GROUP BY u.id
                ),
                -- 2단계: log(1+x) 변환 + Laplace smoothed ratio. 활동 있는 치료사만 필터.
                transformed AS (
                    SELECT
                        *,
                        LN(1 + posts_count)        AS log_posts,
                        LN(1 + useful_received)    AS log_useful,
                        LN(1 + curious_received)   AS log_curious,
                        LN(1 + downloads_received) AS log_downloads,
                        (useful_received + :alpha) / (total_reactions_received + :alpha + :beta)::numeric
                                                   AS useful_ratio_smoothed
                    FROM raw_counts
                    WHERE posts_count > 0
                       OR useful_received > 0
                       OR curious_received > 0
                       OR downloads_received > 0
                ),
                -- 3단계: z-score. 분모 0 보호.
                z_scored AS (
                    SELECT
                        *,
                        (log_posts     - AVG(log_posts)     OVER ()) / NULLIF(STDDEV(log_posts)     OVER (), 0) AS z_posts,
                        (log_useful    - AVG(log_useful)    OVER ()) / NULLIF(STDDEV(log_useful)    OVER (), 0) AS z_useful,
                        (log_curious   - AVG(log_curious)   OVER ()) / NULLIF(STDDEV(log_curious)   OVER (), 0) AS z_curious,
                        (log_downloads - AVG(log_downloads) OVER ()) / NULLIF(STDDEV(log_downloads) OVER (), 0) AS z_downloads,
                        (useful_ratio_smoothed - AVG(useful_ratio_smoothed) OVER ())
                            / NULLIF(STDDEV(useful_ratio_smoothed) OVER (), 0) AS z_useful_ratio
                    FROM transformed
                ),
                -- 4단계: 가중합 → raw_score
                scored AS (
                    SELECT
                        *,
                        COALESCE(z_posts, 0)        * :wPosts
                      + COALESCE(z_useful, 0)       * :wUseful
                      + COALESCE(z_curious, 0)      * :wCurious
                      + COALESCE(z_downloads, 0)    * :wDownloads
                      + COALESCE(z_useful_ratio, 0) * :wUsefulRatio AS raw_score
                    FROM z_scored
                )
                -- 5단계: 최종 INSERT + 백분위 (PERCENT_RANK는 0..1 → ×100)
                INSERT INTO therapist_expertise_daily (
                    user_id, as_of_date, window_days,
                    posts_count, useful_received, curious_received, downloads_received, total_reactions_received,
                    log_posts, log_useful, log_curious, log_downloads,
                    useful_ratio_smoothed,
                    z_posts, z_useful, z_curious, z_downloads, z_useful_ratio,
                    raw_score, rank_percentile,
                    computed_at
                )
                SELECT
                    user_id, :asOfDate, :windowDays,
                    posts_count, useful_received, curious_received, downloads_received, total_reactions_received,
                    log_posts, log_useful, log_curious, log_downloads,
                    useful_ratio_smoothed,
                    z_posts, z_useful, z_curious, z_downloads, z_useful_ratio,
                    raw_score,
                    ROUND((PERCENT_RANK() OVER (ORDER BY raw_score) * 100)::numeric, 2) AS rank_percentile,
                    NOW()
                FROM scored
                """);
        insert.setParameter("windowStart", windowStart);
        insert.setParameter("windowEnd", windowEnd);
        insert.setParameter("asOfDate", asOfDate);
        insert.setParameter("windowDays", WINDOW_DAYS);
        insert.setParameter("alpha", LAPLACE_ALPHA);
        insert.setParameter("beta", LAPLACE_BETA);
        insert.setParameter("wPosts", W_POSTS);
        insert.setParameter("wUseful", W_USEFUL);
        insert.setParameter("wCurious", W_CURIOUS);
        insert.setParameter("wDownloads", W_DOWNLOADS);
        insert.setParameter("wUsefulRatio", W_USEFUL_RATIO);
        int inserted = insert.executeUpdate();

        log.debug("  └ as_of_date {} → {} 치료사 점수 산출", asOfDate, inserted);
    }
}
