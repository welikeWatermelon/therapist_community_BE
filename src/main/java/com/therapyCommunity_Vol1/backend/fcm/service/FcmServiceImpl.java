package com.therapyCommunity_Vol1.backend.fcm.service;

import com.therapyCommunity_Vol1.backend.notification.dto.NotificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "true", matchIfMissing = false)
public class FcmServiceImpl implements FcmService {

    private final Map<Long, String> userTokens = new ConcurrentHashMap<>();

    @Override
    public void sendNotification(Long userId, NotificationResponse notification) {
        String token = userTokens.get(userId);
        if (token != null) {
            // FCM 실제 전송 로직은 여기에 구현
            // Firebase Admin SDK를 사용하여 전송
            log.info("FCM notification would be sent to user: {} with token: {}", userId, token);
        }
    }

    @Override
    public boolean hasToken(Long userId) {
        return userTokens.containsKey(userId);
    }

    @Override
    public void registerToken(Long userId, String token) {
        userTokens.put(userId, token);
        log.info("FCM token registered for user: {}", userId);
    }

    @Override
    public void removeToken(Long userId) {
        userTokens.remove(userId);
        log.info("FCM token removed for user: {}", userId);
    }
}
