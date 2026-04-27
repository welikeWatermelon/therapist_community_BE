package com.therapyCommunity_Vol1.backend.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UnreadConversationCountResponse {

    private long unreadCount;
}
