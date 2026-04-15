package com.therapyCommunity_Vol1.backend.knowledge.event;

import com.therapyCommunity_Vol1.backend.knowledge.domain.KnowledgeDocument;
import com.therapyCommunity_Vol1.backend.knowledge.repository.KnowledgeDocumentRepository;
import com.therapyCommunity_Vol1.backend.knowledge.service.KnowledgeIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentCreatedListener {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeIngestionService ingestionService;

    @Async("knowledgeIngestionExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(KnowledgeDocumentCreatedEvent event) {
        log.info("Knowledge document event received: documentId={}", event.getDocumentId());
        try {
            KnowledgeDocument document = documentRepository.findById(event.getDocumentId())
                    .orElse(null);
            if (document == null) {
                log.warn("Knowledge document not found for event: documentId={}", event.getDocumentId());
                return;
            }

            ingestionService.processDocument(document);
        } catch (Exception e) {
            log.error("Knowledge ingestion event failed: documentId={}", event.getDocumentId(), e);
        }
    }
}
