package com.therapyCommunity_Vol1.backend.chat.domain;

import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "conversations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"participant1_id", "participant2_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant1_id", nullable = false)
    private User participant1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant2_id", nullable = false)
    private User participant2;

    @Column(name = "last_message_content", length = 1000)
    private String lastMessageContent;

    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    public static Conversation create(User participant1, User participant2, String firstMessageContent) {
        User p1, p2;
        if (participant1.getId() < participant2.getId()) {
            p1 = participant1;
            p2 = participant2;
        } else {
            p1 = participant2;
            p2 = participant1;
        }

        Conversation conversation = new Conversation();
        conversation.participant1 = p1;
        conversation.participant2 = p2;
        conversation.lastMessageContent = firstMessageContent;
        conversation.lastMessageAt = LocalDateTime.now();
        return conversation;
    }

    public void updateLastMessage(String content) {
        this.lastMessageContent = content;
        this.lastMessageAt = LocalDateTime.now();
    }

    public boolean isParticipant(Long userId) {
        return participant1.getId().equals(userId) || participant2.getId().equals(userId);
    }

    public User getOtherParticipant(Long myUserId) {
        return participant1.getId().equals(myUserId) ? participant2 : participant1;
    }
}
