package com.therapyCommunity_Vol1.backend.notification.event;

import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NotificationEvent {

    private final Long senderId;
    private final List<Long> receiverIds;
    private final NotificationType type;
    private final Long referenceId;
    private final String content;
}