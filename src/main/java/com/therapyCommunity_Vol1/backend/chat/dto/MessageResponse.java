package com.therapyCommunity_Vol1.backend.chat.dto;

import com.therapyCommunity_Vol1.backend.chat.domain.Message;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class MessageResponse {

    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderNickname;
    private String content;
    private boolean read;
    private LocalDateTime createdAt;

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getId(),
                message.getSender().getDisplayNickname(),
                message.getContent(),
                message.isRead(),
                message.getCreatedAt()
        );
    }
}
