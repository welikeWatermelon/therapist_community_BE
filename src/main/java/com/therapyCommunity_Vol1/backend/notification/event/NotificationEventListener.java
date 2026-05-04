package com.therapyCommunity_Vol1.backend.notification.event;

import com.therapyCommunity_Vol1.backend.notification.service.NotificationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final Counter dbFailureCounter;
    private final Counter unknownFailureCounter;

    public NotificationEventListener(NotificationService notificationService, MeterRegistry meterRegistry) {
        this.notificationService = notificationService;
        this.dbFailureCounter = Counter.builder("notification.failure")
                .tag("cause", "db")
                .description("알림 DB 저장 실패 횟수")
                .register(meterRegistry);
        this.unknownFailureCounter = Counter.builder("notification.failure")
                .tag("cause", "unknown")
                .description("알림 처리 중 예상치 못한 실패 횟수")
                .register(meterRegistry);
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationEvent(NotificationEvent event) {
        try {
            List<NotificationService.SsePayload> payloads = notificationService.createNotifications(event);
            notificationService.sendSseNotifications(payloads);
        } catch (DataAccessException e) {
            dbFailureCounter.increment();
            log.error("알림 DB 저장 실패: type={}, senderId={}, receiverIds={}, referenceId={}",
                    event.getType(), event.getSenderId(), event.getReceiverIds(),
                    event.getReferenceId(), e);
        } catch (Exception e) {
            unknownFailureCounter.increment();
            log.error("알림 처리 실패: type={}, senderId={}, receiverIds={}, referenceId={}",
                    event.getType(), event.getSenderId(), event.getReceiverIds(),
                    event.getReferenceId(), e);
        }
    }
}
