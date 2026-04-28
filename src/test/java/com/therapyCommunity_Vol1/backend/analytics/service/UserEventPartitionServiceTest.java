package com.therapyCommunity_Vol1.backend.analytics.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserEventPartitionServiceTest {

    private EntityManager entityManager;
    private UserEventPartitionService service;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        service = new UserEventPartitionService(entityManager);
    }

    @Test
    void 모든_파티션이_없으면_LOOKAHEAD_플러스1_개를_생성한다() {
        // 존재 여부 조회는 모두 빈 결과 → 다 없음
        Query existsQuery = mock(Query.class);
        when(existsQuery.setParameter(anyString(), anyString())).thenReturn(existsQuery);
        when(existsQuery.getResultList()).thenReturn(Collections.emptyList());

        Query ddlQuery = mock(Query.class);

        when(entityManager.createNativeQuery(contains("FROM pg_class"))).thenReturn(existsQuery);
        when(entityManager.createNativeQuery(contains("CREATE TABLE IF NOT EXISTS"))).thenReturn(ddlQuery);

        int created = service.ensurePartitions();

        // 이번 달 + LOOKAHEAD_MONTHS 후까지 = 4개월치
        assertThat(created).isEqualTo(UserEventPartitionService.LOOKAHEAD_MONTHS + 1);
        verify(ddlQuery, times(UserEventPartitionService.LOOKAHEAD_MONTHS + 1)).executeUpdate();
    }

    @Test
    void 모든_파티션이_이미_있으면_생성하지_않는다() {
        Query existsQuery = mock(Query.class);
        when(existsQuery.setParameter(anyString(), anyString())).thenReturn(existsQuery);
        when(existsQuery.getResultList()).thenReturn(List.of(1));

        when(entityManager.createNativeQuery(contains("FROM pg_class"))).thenReturn(existsQuery);

        int created = service.ensurePartitions();

        assertThat(created).isZero();
        verify(entityManager, never()).createNativeQuery(contains("CREATE TABLE"));
    }

    @Test
    void 생성_쿼리는_올바른_월_경계를_사용한다() {
        Query existsQuery = mock(Query.class);
        when(existsQuery.setParameter(anyString(), anyString())).thenReturn(existsQuery);
        when(existsQuery.getResultList()).thenReturn(Collections.emptyList());

        Query ddlQuery = mock(Query.class);

        when(entityManager.createNativeQuery(contains("FROM pg_class"))).thenReturn(existsQuery);
        when(entityManager.createNativeQuery(contains("CREATE TABLE IF NOT EXISTS"))).thenReturn(ddlQuery);

        service.ensurePartitions();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, atLeastOnce()).createNativeQuery(sqlCaptor.capture());

        YearMonth current = YearMonth.now();
        for (int i = 0; i <= UserEventPartitionService.LOOKAHEAD_MONTHS; i++) {
            YearMonth target = current.plusMonths(i);
            String expectedName = String.format("user_events_%04d_%02d",
                    target.getYear(), target.getMonthValue());
            String expectedFrom = target.atDay(1).toString();
            String expectedTo = target.plusMonths(1).atDay(1).toString();

            assertThat(sqlCaptor.getAllValues())
                    .anyMatch(sql -> sql.contains(expectedName)
                            && sql.contains(expectedFrom)
                            && sql.contains(expectedTo));
        }
    }
}
