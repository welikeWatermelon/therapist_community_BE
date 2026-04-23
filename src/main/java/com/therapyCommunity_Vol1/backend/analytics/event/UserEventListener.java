package com.therapyCommunity_Vol1.backend.analytics.event;

import com.therapyCommunity_Vol1.backend.analytics.domain.UserEvent;
import com.therapyCommunity_Vol1.backend.analytics.repository.UserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

    private final UserEventRepository userEventRepository;

    @Async("analyticsExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void handle(UserEventPayload payload) {
        try {
            userEventRepository.save(UserEvent.of(
                    payload.getUserId(),
                    payload.getEventType(),
                    payload.getTargetType(),
                    payload.getTargetId(),
                    payload.getMetadata(),
                    payload.getOccurredAt()
            ));
        } catch (Exception e) {
            log.error("analytics event persist 실패: type={}, userId={}, targetId={}",
                    payload.getEventType(), payload.getUserId(), payload.getTargetId(), e);
        }
    }
}
