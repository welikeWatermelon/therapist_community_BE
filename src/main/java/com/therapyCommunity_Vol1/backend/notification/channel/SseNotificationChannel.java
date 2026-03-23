package com.therapyCommunity_Vol1.backend.notification.channel;

import com.therapyCommunity_Vol1.backend.notification.dto.NotificationResponse;
import com.therapyCommunity_Vol1.backend.sse.manager.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SseNotificationChannel implements NotificationChannel {

    private static final String EVENT_NAME = "notification";
    private final SseEmitterManager sseEmitterManager;

    @Override
    public void send(Long userId, NotificationResponse notification) {
        if (supports(userId)) {
            sseEmitterManager.sendToUser(userId, EVENT_NAME, notification);
            log.debug("SSE notification sent to user: {}", userId);
        }
    }

    @Override
    public boolean supports(Long userId) {
        return sseEmitterManager.isUserOnline(userId);
    }

    @Override
    public String getChannelName() {
        return "SSE";
    }
}
