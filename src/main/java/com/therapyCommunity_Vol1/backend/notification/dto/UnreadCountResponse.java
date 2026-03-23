package com.therapyCommunity_Vol1.backend.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UnreadCountResponse {
    private long unreadCount;

    public static UnreadCountResponse of(long count) {
        return new UnreadCountResponse(count);
    }
}
