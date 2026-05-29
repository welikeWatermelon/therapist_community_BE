package com.therapyCommunity_Vol1.backend.analytics.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * user_events 월별 파티션을 미리 생성하는 유지보수 서비스.
 *
 * V28 부트스트랩은 4개월치만 만들어두므로, 이후 월이 도래하기 전에 자동으로 채워줘야 한다.
 * 파티션이 없는 시점의 INSERT는 PG가 거부하므로 본 서비스가 빠지면 이벤트 유실로 직결.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventPartitionService {

    /** 이번 달 + 이후 3개월 = 총 4개월치를 항상 보장. 매월 1회 실행이 1번 누락돼도 안전. */
    static final int LOOKAHEAD_MONTHS = 3;

    private final EntityManager entityManager;

    /**
     * 현재 달부터 LOOKAHEAD_MONTHS 후까지의 파티션이 없으면 생성.
     * 이미 존재하는 파티션은 건드리지 않음. 새로 만든 개수를 반환.
     */
    @Transactional
    public int ensurePartitions() {
        YearMonth current = YearMonth.now();
        int created = 0;

        for (int i = 0; i <= LOOKAHEAD_MONTHS; i++) {
            YearMonth target = current.plusMonths(i);
            String partitionName = partitionNameOf(target);

            if (partitionExists(partitionName)) {
                continue;
            }

            createPartition(target, partitionName);
            log.info("user_events 파티션 생성: {} ({} ~ {})",
                    partitionName, target.atDay(1), target.plusMonths(1).atDay(1));
            created++;
        }
        return created;
    }

    static String partitionNameOf(YearMonth month) {
        return String.format("user_events_%04d_%02d", month.getYear(), month.getMonthValue());
    }

    private boolean partitionExists(String partitionName) {
        Query q = entityManager.createNativeQuery(
                "SELECT 1 FROM pg_class WHERE relname = :name AND relkind = 'r'");
        q.setParameter("name", partitionName);
        return !q.getResultList().isEmpty();
    }

    private void createPartition(YearMonth month, String partitionName) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.plusMonths(1).atDay(1);

        // partitionName/start/end는 모두 내부 계산값이라 SQL 인젝션 위험 없음.
        // DDL identifier에는 native 파라미터 바인딩이 안 되므로 String.format 사용.
        // IF NOT EXISTS는 동시 실행/race 보호 (앞서 partitionExists로 한 번 더 거름).
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS %s PARTITION OF user_events " +
                        "FOR VALUES FROM ('%s') TO ('%s')",
                partitionName, start, end);
        entityManager.createNativeQuery(sql).executeUpdate();
    }
}
