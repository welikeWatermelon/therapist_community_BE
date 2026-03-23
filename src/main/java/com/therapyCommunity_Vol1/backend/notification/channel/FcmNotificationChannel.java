package com.therapyCommunity_Vol1.backend.notification.channel;

import com.therapyCommunity_Vol1.backend.fcm.service.FcmService;
import com.therapyCommunity_Vol1.backend.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(2)
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class FcmNotificationChannel implements NotificationChannel {

    private final FcmService fcmService;

    @Override
    public void send(Long userId, NotificationResponse notification) {
        if (supports(userId)) {
            fcmService.sendNotification(userId, notification);
            log.debug("FCM notification sent to user: {}", userId);
        }
    }

    @Override
    public boolean supports(Long userId) {
        return fcmService.hasToken(userId);
    }

    @Override
    public String getChannelName() {
        return "FCM";
    }
}
