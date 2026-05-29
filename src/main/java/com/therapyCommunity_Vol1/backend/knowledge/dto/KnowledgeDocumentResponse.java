package com.therapyCommunity_Vol1.backend.knowledge.dto;

import com.therapyCommunity_Vol1.backend.knowledge.domain.KnowledgeDocument;

import java.time.LocalDateTime;

public record KnowledgeDocumentResponse(
        Long id,
        String sourceType,
        String title,
        String therapyArea,
        String status,
        int attemptCount,
        String lastErrorCode,
        String lastErrorMessage,
        LocalDateTime processedAt,
        LocalDateTime createdAt
) {
    public static KnowledgeDocumentResponse from(KnowledgeDocument doc) {
        return new KnowledgeDocumentResponse(
                doc.getId(),
                doc.getSourceType(),
                doc.getTitle(),
                doc.getTherapyArea() != null ? doc.getTherapyArea().name() : null,
                doc.getStatus().name(),
                doc.getAttemptCount(),
                doc.getLastErrorCode(),
                doc.getLastErrorMessage(),
                doc.getProcessedAt(),
                doc.getCreatedAt()
        );
    }
}
