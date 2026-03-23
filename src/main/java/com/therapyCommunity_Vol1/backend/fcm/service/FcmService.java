package com.therapyCommunity_Vol1.backend.fcm.service;

import com.therapyCommunity_Vol1.backend.notification.dto.NotificationResponse;

public interface FcmService {

    void sendNotification(Long userId, NotificationResponse notification);

    boolean hasToken(Long userId);

    void registerToken(Long userId, String token);

    void removeToken(Long userId);
}
