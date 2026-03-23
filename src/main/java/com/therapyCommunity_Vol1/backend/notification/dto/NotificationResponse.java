package com.therapyCommunity_Vol1.backend.notification.dto;

import com.therapyCommunity_Vol1.backend.notification.domain.Notification;
import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationResponse {
    private Long id;
    private Long actorId;
    private String actorNickname;
    private String actorProfileImageUrl;
    private NotificationType notificationType;
    private Long referenceId;
    private String referenceType;
    private String message;
    private boolean isRead;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .actorId(notification.getActor().getId())
                .actorNickname(notification.getActor().getNickname())
                .actorProfileImageUrl(notification.getActor().getProfileImageUrl())
                .notificationType(notification.getNotificationType())
                .referenceId(notification.getReferenceId())
                .referenceType(notification.getReferenceType())
                .message(notification.getMessage())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
