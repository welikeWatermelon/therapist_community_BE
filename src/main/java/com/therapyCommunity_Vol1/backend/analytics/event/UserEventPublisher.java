package com.therapyCommunity_Vol1.backend.analytics.event;

import com.therapyCommunity_Vol1.backend.analytics.domain.EventTargetType;
import com.therapyCommunity_Vol1.backend.analytics.domain.UserEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publish(
            Long userId,
            UserEventType eventType,
            EventTargetType targetType,
            Long targetId,
            Map<String, Object> metadata
    ) {
        applicationEventPublisher.publishEvent(UserEventPayload.builder()
                .userId(userId)
                .eventType(eventType)
                .targetType(targetType)
                .targetId(targetId)
                .metadata(metadata)
                .occurredAt(LocalDateTime.now())
                .build());
    }

    public void publish(Long userId, UserEventType eventType, EventTargetType targetType, Long targetId) {
        publish(userId, eventType, targetType, targetId, null);
    }
}
