package com.therapyCommunity_Vol1.backend.chat.dto;

import com.therapyCommunity_Vol1.backend.chat.domain.Conversation;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ConversationResponse {

    private Long id;
    private Long otherUserId;
    private String otherUserNickname;
    private String otherUserProfileImageUrl;
    private String lastMessageContent;
    private LocalDateTime lastMessageAt;
    private long unreadCount;
    private LocalDateTime createdAt;

    public static ConversationResponse from(Conversation conversation, Long myUserId, long unreadCount) {
        User other = conversation.getOtherParticipant(myUserId);
        return new ConversationResponse(
                conversation.getId(),
                other.getId(),
                other.getDisplayNickname(),
                other.getProfileImageUrl(),
                conversation.getLastMessageContent(),
                conversation.getLastMessageAt(),
                unreadCount,
                conversation.getCreatedAt()
        );
    }
}
