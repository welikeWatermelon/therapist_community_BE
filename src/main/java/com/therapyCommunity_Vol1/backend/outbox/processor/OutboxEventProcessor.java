package com.therapyCommunity_Vol1.backend.outbox.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therapyCommunity_Vol1.backend.notification.channel.NotificationChannel;
import com.therapyCommunity_Vol1.backend.notification.dto.NotificationResponse;
import com.therapyCommunity_Vol1.backend.outbox.domain.OutboxEvent;
import com.therapyCommunity_Vol1.backend.outbox.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventProcessor {

    private static final String EVENT_TYPE_NOTIFICATION = "NOTIFICATION";

    private final OutboxService outboxService;
    private final List<NotificationChannel> notificationChannels;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEvent(OutboxEvent event) {
        try {
            if (EVENT_TYPE_NOTIFICATION.equals(event.getEventType())) {
                processNotificationEvent(event);
            }
            outboxService.markAsCompleted(event);
            log.debug("Outbox event processed successfully: {}", event.getId());
        } catch (Exception e) {
            log.error("Failed to process outbox event: {}", event.getId(), e);
            outboxService.markAsFailed(event);
        }
    }

    @SuppressWarnings("unchecked")
    private void processNotificationEvent(OutboxEvent event) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(event.getPayload(), Map.class);
        Long recipientId = Long.valueOf(payload.get("recipientId").toString());

        // payload에서 NotificationResponse 정보 추출
        NotificationResponse notification = objectMapper.convertValue(
                payload.get("notification"), NotificationResponse.class);

        // 모든 지원 가능한 채널로 전송
        for (NotificationChannel channel : notificationChannels) {
            if (channel.supports(recipientId)) {
                channel.send(recipientId, notification);
                log.debug("Notification sent via {} to user: {}", channel.getChannelName(), recipientId);
            }
        }
    }
}
