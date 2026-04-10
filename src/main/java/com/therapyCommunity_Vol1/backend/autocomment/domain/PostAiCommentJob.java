package com.therapyCommunity_Vol1.backend.autocomment.domain;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "post_ai_comment_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostAiCommentJob extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false, unique = true)
    private TherapyPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedBy;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private TherapyPostComment comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AutoCommentJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 20)
    private ReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_mode", length = 20)
    private SourceMode sourceMode;

    @Column(name = "draft_comment", columnDefinition = "TEXT")
    private String draftComment;

    @Column(name = "retrieval_context_json", columnDefinition = "JSONB")
    private String retrievalContextJson;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error_code", length = 50)
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public static PostAiCommentJob create(TherapyPost post, User requestedBy) {
        PostAiCommentJob job = new PostAiCommentJob();
        job.post = post;
        job.requestedBy = requestedBy;
        job.status = AutoCommentJobStatus.QUEUED;
        job.attemptCount = 0;
        return job;
    }

    public static PostAiCommentJob createFailed(TherapyPost post, User requestedBy, String errorCode, String errorMessage) {
        PostAiCommentJob job = new PostAiCommentJob();
        job.post = post;
        job.requestedBy = requestedBy;
        job.status = AutoCommentJobStatus.FAILED;
        job.attemptCount = 0;
        job.lastErrorCode = errorCode;
        job.lastErrorMessage = errorMessage;
        return job;
    }

    public boolean isTerminal() {
        return status == AutoCommentJobStatus.SUCCEEDED
                || status == AutoCommentJobStatus.FAILED
                || status == AutoCommentJobStatus.CANCELLED;
    }

    public void markProcessing() {
        this.status = AutoCommentJobStatus.PROCESSING;
        this.attemptCount++;
    }

    public void markSucceeded(String draftComment, String retrievalContextJson, SourceMode sourceMode, Double confidenceScore) {
        this.status = AutoCommentJobStatus.SUCCEEDED;
        this.reviewStatus = ReviewStatus.PENDING_REVIEW;
        this.draftComment = draftComment;
        this.retrievalContextJson = retrievalContextJson;
        this.sourceMode = sourceMode;
        this.confidenceScore = confidenceScore;
        this.processedAt = LocalDateTime.now();
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    public void markCancelled() {
        this.status = AutoCommentJobStatus.CANCELLED;
    }

    public void markFailed(String errorCode, String errorMessage, LocalDateTime nextAttempt) {
        if (nextAttempt != null) {
            this.status = AutoCommentJobStatus.QUEUED;
            this.nextAttemptAt = nextAttempt;
        } else {
            this.status = AutoCommentJobStatus.FAILED;
        }
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
    }

    public boolean isRetryable() {
        return this.attemptCount < 4;
    }

    public void approve(User admin, TherapyPostComment comment) {
        this.reviewStatus = ReviewStatus.APPROVED;
        this.reviewedBy = admin;
        this.reviewedAt = LocalDateTime.now();
        this.comment = comment;
    }

    public void reject(User admin) {
        this.reviewStatus = ReviewStatus.REJECTED;
        this.reviewedBy = admin;
        this.reviewedAt = LocalDateTime.now();
    }
}
