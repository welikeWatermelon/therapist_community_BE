package com.therapyCommunity_Vol1.backend.outbox.repository;

import com.therapyCommunity_Vol1.backend.outbox.domain.OutboxEvent;
import com.therapyCommunity_Vol1.backend.outbox.domain.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = :status ORDER BY e.createdAt ASC")
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(@Param("status") OutboxEventStatus status);

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents();

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'FAILED' AND e.retryCount < e.maxRetries ORDER BY e.createdAt ASC")
    List<OutboxEvent> findRetryableFailedEvents();
}
