package com.therapyCommunity_Vol1.backend.autocomment.event;

import com.therapyCommunity_Vol1.backend.autocomment.domain.PostAiCommentJob;
import com.therapyCommunity_Vol1.backend.autocomment.repository.PostAiCommentJobRepository;
import com.therapyCommunity_Vol1.backend.autocomment.service.AiCommentJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCommentRequestedListener {

    private final PostAiCommentJobRepository jobRepository;
    private final AiCommentJobService jobService;

    @Async("aiCommentExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AutoCommentRequestedEvent event) {
        try {
            log.info("AI comment event received: jobId={}", event.getJobId());
            jobService.processJob(event.getJobId());
        } catch (Exception e) {
            log.error("AI comment event failed: jobId={}", event.getJobId(), e);
        }
    }
}
