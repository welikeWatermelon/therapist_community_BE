package com.therapyCommunity_Vol1.backend.notification.channel;

import com.therapyCommunity_Vol1.backend.notification.dto.NotificationResponse;

public interface NotificationChannel {

    void send(Long userId, NotificationResponse notification);

    boolean supports(Long userId);

    String getChannelName();
}
