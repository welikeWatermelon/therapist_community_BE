package com.therapyCommunity_Vol1.backend.message.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.therapyCommunity_Vol1.backend.message.domain.Message;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class MessageResponse {

    private Long messageId;
    private Long senderId;
    private String senderNickname;
    private Long receiverId;
    private String receiverNickname;
    private String content;

    @JsonProperty("isRead")
    private boolean isRead;

    @JsonProperty("isBroadcast")
    private boolean isBroadcast;

    private LocalDateTime createdAt;

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getSender().getId(),
                message.getSender().getDisplayNickname(),
                message.getReceiver().getId(),
                message.getReceiver().getDisplayNickname(),
                message.getContent(),
                message.isRead(),
                message.getBroadcastId() != null,
                message.getCreatedAt()
        );
    }
}
