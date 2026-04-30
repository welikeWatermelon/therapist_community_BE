package com.therapyCommunity_Vol1.backend.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "aggregation_progress")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AggregationProgress {

    @Id
    @Column(name = "job_name", length = 100)
    private String jobName;

    @Column(name = "last_window_end", nullable = false)
    private LocalDateTime lastWindowEnd;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void advance(LocalDateTime newWindowEnd) {
        this.lastWindowEnd = newWindowEnd;
        this.updatedAt = LocalDateTime.now();
    }
}
