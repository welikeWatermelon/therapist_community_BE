package com.therapyCommunity_Vol1.backend.notification.domain;

import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "reference_type", nullable = false, length = 50)
    private String referenceType;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Builder
    private Notification(User recipient, User actor, NotificationType notificationType,
                         Long referenceId, String referenceType, String message) {
        this.recipient = recipient;
        this.actor = actor;
        this.notificationType = notificationType;
        this.referenceId = referenceId;
        this.referenceType = referenceType;
        this.message = message;
        this.isRead = false;
    }

    public static Notification create(User recipient, User actor, NotificationType notificationType,
                                       Long referenceId, String referenceType, String message) {
        return Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .notificationType(notificationType)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .message(message)
                .build();
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
