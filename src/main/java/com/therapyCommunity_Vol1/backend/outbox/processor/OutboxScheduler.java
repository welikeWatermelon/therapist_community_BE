package com.therapyCommunity_Vol1.backend.outbox.processor;

import com.therapyCommunity_Vol1.backend.outbox.domain.OutboxEvent;
import com.therapyCommunity_Vol1.backend.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventProcessor outboxEventProcessor;

    @Scheduled(fixedDelay = 500) // 500ms 폴링
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents();

        if (!pendingEvents.isEmpty()) {
            log.debug("Processing {} pending outbox events", pendingEvents.size());
        }

        for (OutboxEvent event : pendingEvents) {
            try {
                outboxEventProcessor.processEvent(event);
            } catch (Exception e) {
                log.error("Error processing outbox event: {}", event.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelay = 60000) // 1분마다 실패한 이벤트 재시도
    public void retryFailedEvents() {
        List<OutboxEvent> failedEvents = outboxEventRepository.findRetryableFailedEvents();

        if (!failedEvents.isEmpty()) {
            log.info("Retrying {} failed outbox events", failedEvents.size());
        }

        for (OutboxEvent event : failedEvents) {
            try {
                outboxEventProcessor.processEvent(event);
            } catch (Exception e) {
                log.error("Error retrying outbox event: {}", event.getId(), e);
            }
        }
    }
}
