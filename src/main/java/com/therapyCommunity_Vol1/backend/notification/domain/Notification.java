package com.therapyCommunity_Vol1.backend.notification.domain;

import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public static Notification create(
            User receiver,
            User sender,
            NotificationType type,
            Long referenceId,
            String content
    ) {
        return create(receiver, sender, type, referenceId, null, content);
    }

    public static Notification create(
            User receiver,
            User sender,
            NotificationType type,
            Long referenceId,
            Long postId,
            String content
    ) {
        Notification n = new Notification();
        n.receiver = receiver;
        n.sender = sender;
        n.notificationType = type;
        n.referenceId = referenceId;
        n.postId = postId;
        n.content = content;
        n.read = false;
        return n;
    }

    public void markAsRead() {
        if (!this.read) {
            this.read = true;
            this.readAt = LocalDateTime.now();
        }
    }
}