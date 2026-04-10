package com.therapyCommunity_Vol1.backend.knowledge.domain;

import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "therapy_area", length = 50)
    private TherapyArea therapyArea;

    @Column(name = "rights_status", nullable = false, length = 50)
    private String rightsStatus;

    @Column(name = "source_uri")
    private String sourceUri;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(name = "extraction_mode", nullable = false, length = 50)
    private String extractionMode;

    @Column(name = "stored_path")
    private String storedPath;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error_code", length = 50)
    private String lastErrorCode;

    @Column(name = "last_error_message")
    private String lastErrorMessage;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public static KnowledgeDocument create(
            String sourceType,
            String title,
            TherapyArea therapyArea,
            String rightsStatus,
            String checksum,
            String extractionMode,
            String storedPath,
            String originalFilename,
            String contentType,
            Long fileSize
    ) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.sourceType = sourceType;
        doc.title = title;
        doc.therapyArea = therapyArea;
        doc.rightsStatus = rightsStatus;
        doc.checksum = checksum;
        doc.extractionMode = extractionMode;
        doc.storedPath = storedPath;
        doc.originalFilename = originalFilename;
        doc.contentType = contentType;
        doc.fileSize = fileSize;
        doc.status = DocumentStatus.QUEUED;
        doc.attemptCount = 0;
        return doc;
    }

    public void markProcessing() {
        this.status = DocumentStatus.PROCESSING;
        this.attemptCount++;
    }

    public void markReady() {
        this.status = DocumentStatus.READY;
        this.processedAt = LocalDateTime.now();
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
    }

    public void markFailed(String errorCode, String errorMessage, LocalDateTime nextAttempt) {
        this.status = DocumentStatus.FAILED;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        this.nextAttemptAt = nextAttempt;
    }

    public void retry() {
        this.status = DocumentStatus.QUEUED;
        this.nextAttemptAt = null;
    }

    public boolean isRetryable() {
        return this.attemptCount < 4;
    }
}
