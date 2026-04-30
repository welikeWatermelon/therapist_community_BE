package com.therapyCommunity_Vol1.backend.post.event;

import com.therapyCommunity_Vol1.backend.post.service.search.EmbeddingFailureRecorder;
import com.therapyCommunity_Vol1.backend.post.service.search.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.search.embedding.enabled", havingValue = "true")
public class EmbeddingEventListener {

    private final EmbeddingService embeddingService;
    private final EmbeddingFailureRecorder failureRecorder;

    @Async("embeddingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleEmbeddingEvent(EmbeddingEvent event) {
        try {
            embeddingService.generateAndSave(event.getPostId(), event.getText());
        } catch (Exception e) {
            log.error("임베딩 생성 실패: postId={}", event.getPostId(), e);
            try {
                failureRecorder.markFailed(event.getPostId());
            } catch (Exception ex) {
                log.error("임베딩 실패 마킹 오류: postId={}", event.getPostId(), ex);
            }
        }
    }
}
