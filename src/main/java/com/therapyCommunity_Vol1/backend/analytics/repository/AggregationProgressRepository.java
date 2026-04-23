package com.therapyCommunity_Vol1.backend.analytics.repository;

import com.therapyCommunity_Vol1.backend.analytics.domain.AggregationProgress;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface AggregationProgressRepository extends JpaRepository<AggregationProgress, String> {

    /**
     * 동시에 한 인스턴스만 해당 잡을 집계하도록 PESSIMISTIC_WRITE로 커서 행 잠금.
     * 멀티 인스턴스 스케줄러가 같은 시각에 트리거돼도 이 호출이 뒤엣놈을 막아줌.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AggregationProgress> findByJobName(String jobName);
}
