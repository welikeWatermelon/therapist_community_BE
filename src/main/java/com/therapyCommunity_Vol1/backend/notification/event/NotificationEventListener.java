package com.therapyCommunity_Vol1.backend.notification.event;

import com.therapyCommunity_Vol1.backend.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationEvent(NotificationEvent event) {
        try {
            notificationService.createAndSend(event);
        } catch (Exception e) {
            log.error("알림 처리 실패: type={}, senderId={}", event.getType(), event.getSenderId(), e);
        }
    }
}
