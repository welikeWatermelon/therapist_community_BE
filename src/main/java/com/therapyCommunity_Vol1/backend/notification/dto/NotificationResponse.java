package com.therapyCommunity_Vol1.backend.notification.dto;

import com.therapyCommunity_Vol1.backend.notification.domain.Notification;
import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private NotificationType type;
    private String content;
    private Long referenceId;
    private Long postId;
    private Long senderId;
    private String senderNickname;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getNotificationType(),
                notification.getContent(),
                notification.getReferenceId(),
                notification.getPostId(),
                notification.getSender() != null ? notification.getSender().getId() : null,
                notification.getSender() != null ? notification.getSender().getDisplayNickname() : null,
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}