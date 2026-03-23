package com.therapyCommunity_Vol1.backend.outbox.domain;

import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private OutboxEventStatus status = OutboxEventStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Builder
    private OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.retryCount = 0;
        this.maxRetries = 3;
    }

    public static OutboxEvent create(String aggregateType, String aggregateId,
                                      String eventType, String payload) {
        return OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .build();
    }

    public void markAsCompleted() {
        this.status = OutboxEventStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
    }

    public void markAsFailed() {
        this.retryCount++;
        if (this.retryCount >= this.maxRetries) {
            this.status = OutboxEventStatus.FAILED;
        }
        this.processedAt = LocalDateTime.now();
    }

    public boolean canRetry() {
        return this.retryCount < this.maxRetries;
    }
}
