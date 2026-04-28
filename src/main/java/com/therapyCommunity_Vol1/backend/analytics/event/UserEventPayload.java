package com.therapyCommunity_Vol1.backend.analytics.event;

import com.therapyCommunity_Vol1.backend.analytics.domain.EventTargetType;
import com.therapyCommunity_Vol1.backend.analytics.domain.UserEventType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class UserEventPayload {

    private final Long userId;
    private final UserEventType eventType;
    private final EventTargetType targetType;
    private final Long targetId;
    private final Map<String, Object> metadata;
    private final LocalDateTime occurredAt;
}
