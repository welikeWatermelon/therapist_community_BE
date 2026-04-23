package com.therapyCommunity_Vol1.backend.analytics.event;

import com.therapyCommunity_Vol1.backend.analytics.domain.UserEvent;
import com.therapyCommunity_Vol1.backend.analytics.repository.UserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

    private final UserEventRepository userEventRepository;

    /**
     * AFTER_COMMIT은 원본 TX가 이미 끝난 상태에서 호출되고, @Async로 다른 스레드에서 실행됨.
     * 따라서 save() 호출을 감쌀 TX가 없음 → REQUIRES_NEW로 새 TX를 연다.
     * (Spring은 @TransactionalEventListener + @Transactional 조합에 REQUIRED 사용을 막음)
     */
    @Async("analyticsExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
