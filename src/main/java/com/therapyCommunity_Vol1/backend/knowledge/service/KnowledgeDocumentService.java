package com.therapyCommunity_Vol1.backend.knowledge.service;

import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.knowledge.config.KnowledgeProperties;
import com.therapyCommunity_Vol1.backend.knowledge.domain.DocumentStatus;
import com.therapyCommunity_Vol1.backend.knowledge.domain.KnowledgeDocument;
import com.therapyCommunity_Vol1.backend.knowledge.dto.KnowledgeDocumentResponse;
import com.therapyCommunity_Vol1.backend.knowledge.event.KnowledgeDocumentCreatedEvent;
import com.therapyCommunity_Vol1.backend.knowledge.repository.KnowledgeChunkSearchRepository;
import com.therapyCommunity_Vol1.backend.knowledge.dto.ChunkSearchResult;
import com.therapyCommunity_Vol1.backend.knowledge.repository.KnowledgeDocumentRepository;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KnowledgeDocumentService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkSearchRepository chunkSearchRepository;
    private final FileStorageService fileStorageService;
    private final KnowledgeProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public KnowledgeDocumentResponse upload(MultipartFile file, String title, TherapyArea therapyArea,
                                             String sourceType, String rightsStatus) {
        String checksum = computeChecksum(file);
        if (documentRepository.existsByChecksum(checksum)) {
            throw new CustomException(ErrorCode.CONFLICT);
        }

        StoredFileInfo storedFile = fileStorageService.storeKnowledgeDocument(file);

        try {
            KnowledgeDocument document = KnowledgeDocument.create(
                    sourceType, title, therapyArea, rightsStatus, checksum, "TEXT",
                    storedFile.getStoredPath(), storedFile.getOriginalFilename(),
                    storedFile.getContentType(), file.getSize()
            );

            if (!properties.isEnabled()) {
                document.markFailed("FEATURE_DISABLED", "Knowledge ingestion is disabled");
            }

            KnowledgeDocument saved = documentRepository.save(document);

            if (properties.isEnabled()) {
                eventPublisher.publishEvent(new KnowledgeDocumentCreatedEvent(saved.getId()));
            }

            return KnowledgeDocumentResponse.from(saved);
        } catch (RuntimeException e) {
            // DB 저장 실패 시 파일 orphan 방지
            try {
                fileStorageService.delete(storedFile.getStoredPath());
            } catch (Exception deleteEx) {
                log.warn("Failed to cleanup orphan file: {}", storedFile.getStoredPath(), deleteEx);
            }
            throw e;
        }
    }

    public Page<KnowledgeDocument> list(Pageable pageable) {
        return documentRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public KnowledgeDocument findOrThrow(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional
    public KnowledgeDocumentResponse retry(Long documentId) {
        KnowledgeDocument doc = findOrThrow(documentId);

        if (doc.getStatus() != DocumentStatus.FAILED) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        doc.retry();
        documentRepository.save(doc);
        eventPublisher.publishEvent(new KnowledgeDocumentCreatedEvent(doc.getId()));

        return KnowledgeDocumentResponse.from(doc);
    }

    public List<ChunkSearchResult> findSimilarChunks(float[] queryEmbedding, TherapyArea therapyArea, int topK) {
        return chunkSearchRepository.findSimilarChunks(queryEmbedding, therapyArea, topK);
    }

    private String computeChecksum(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
