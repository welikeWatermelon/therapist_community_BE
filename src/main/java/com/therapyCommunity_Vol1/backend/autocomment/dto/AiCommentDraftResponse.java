package com.therapyCommunity_Vol1.backend.autocomment.dto;

import com.therapyCommunity_Vol1.backend.autocomment.domain.PostAiCommentJob;

import java.time.LocalDateTime;

public record AiCommentDraftResponse(
        Long jobId,
        Long postId,
        String status,
        String reviewStatus,
        String sourceMode,
        Double confidenceScore,
        String draftComment,
        String retrievalContextJson,
        LocalDateTime processedAt,
        LocalDateTime createdAt
) {
    public static AiCommentDraftResponse from(PostAiCommentJob job) {
        return new AiCommentDraftResponse(
                job.getId(),
                job.getPost().getId(),
                job.getStatus().name(),
                job.getReviewStatus() != null ? job.getReviewStatus().name() : null,
                job.getSourceMode() != null ? job.getSourceMode().name() : null,
                job.getConfidenceScore(),
                job.getDraftComment(),
                job.getRetrievalContextJson(),
                job.getProcessedAt(),
                job.getCreatedAt()
        );
    }
}
