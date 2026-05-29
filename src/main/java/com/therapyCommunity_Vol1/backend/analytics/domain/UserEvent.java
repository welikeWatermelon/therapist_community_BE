package com.therapyCommunity_Vol1.backend.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Entity
@Table(name = "user_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private UserEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 30)
    private EventTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    private UserEvent(
            Long userId,
            UserEventType eventType,
            EventTargetType targetType,
            Long targetId,
            Map<String, Object> metadata,
            LocalDateTime occurredAt
    ) {
        this.userId = userId;
        this.eventType = eventType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.metadata = metadata;
        this.occurredAt = occurredAt;
    }

    public static UserEvent of(
            Long userId,
            UserEventType eventType,
            EventTargetType targetType,
            Long targetId,
            Map<String, Object> metadata,
            LocalDateTime occurredAt
    ) {
        return new UserEvent(userId, eventType, targetType, targetId, metadata, occurredAt);
    }
}
