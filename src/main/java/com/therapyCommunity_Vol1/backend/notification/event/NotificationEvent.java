package com.therapyCommunity_Vol1.backend.notification.event;

import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder(access = AccessLevel.PRIVATE)
public class NotificationEvent {

    private final Long senderId;
    private final List<Long> receiverIds;
    private final NotificationType type;
    private final Long referenceId;
    private final Long postId;
    private final List<String> extraParams;

    public static NotificationEvent of(
            Long senderId, Long receiverId,
            NotificationType type, Long referenceId) {
        return NotificationEvent.builder()
                .senderId(senderId)
                .receiverIds(List.of(receiverId))
                .type(type)
                .referenceId(referenceId)
                .build();
    }

    public static NotificationEvent of(
            Long senderId, Long receiverId,
            NotificationType type, Long referenceId,
            String... extraParams) {
        return NotificationEvent.builder()
                .senderId(senderId)
                .receiverIds(List.of(receiverId))
                .type(type)
                .referenceId(referenceId)
                .extraParams(List.of(extraParams))
                .build();
    }

    public static NotificationEvent of(
            Long senderId, Long receiverId,
            NotificationType type, Long referenceId,
            Long postId, String... extraParams) {
        return NotificationEvent.builder()
                .senderId(senderId)
                .receiverIds(List.of(receiverId))
                .type(type)
                .referenceId(referenceId)
                .postId(postId)
                .extraParams(List.of(extraParams))
                .build();
    }

    public static NotificationEvent of(
            Long senderId, List<Long> receiverIds,
            NotificationType type, Long referenceId) {
        return NotificationEvent.builder()
                .senderId(senderId)
                .receiverIds(receiverIds)
                .type(type)
                .referenceId(referenceId)
                .build();
    }
}