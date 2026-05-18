package com.therapyCommunity_Vol1.backend.message.domain;

import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id")
    private User receiver;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "deleted_by_sender", nullable = false)
    private boolean deletedBySender;

    @Column(name = "deleted_by_receiver", nullable = false)
    private boolean deletedByReceiver;

    @Column(name = "broadcast_id")
    private UUID broadcastId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Message(User sender, User receiver, String content, UUID broadcastId) {
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.broadcastId = broadcastId;
        this.isRead = false;
        this.deletedBySender = false;
        this.deletedByReceiver = false;
    }

    public static Message create(User sender, User receiver, String content) {
        return new Message(sender, receiver, content, null);
    }

    public static Message createBroadcast(User sender, User receiver, String content, UUID broadcastId) {
        return new Message(sender, receiver, content, broadcastId);
    }

    public void markAsRead() {
        if (!this.isRead) {
            this.isRead = true;
            this.readAt = LocalDateTime.now();
        }
    }

    public void deleteBySender() {
        this.deletedBySender = true;
    }

    public void deleteByReceiver() {
        this.deletedByReceiver = true;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public boolean isFullyDeleted() {
        return this.deletedBySender && this.deletedByReceiver;
    }

    public boolean isSender(Long userId) {
        return this.sender.getId().equals(userId);
    }

    public boolean isReceiver(Long userId) {
        return this.receiver.getId().equals(userId);
    }

    public boolean isParticipant(Long userId) {
        return isSender(userId) || isReceiver(userId);
    }
}
