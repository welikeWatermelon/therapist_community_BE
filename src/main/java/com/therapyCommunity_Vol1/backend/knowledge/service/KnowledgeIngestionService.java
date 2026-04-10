package com.therapyCommunity_Vol1.backend.knowledge.service;

import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.knowledge.client.GeminiEmbeddingClient;
import com.therapyCommunity_Vol1.backend.knowledge.config.KnowledgeProperties;
import com.therapyCommunity_Vol1.backend.knowledge.domain.*;
import com.therapyCommunity_Vol1.backend.knowledge.extraction.DocumentExtractor;
import com.therapyCommunity_Vol1.backend.knowledge.extraction.ExtractedDocument;
import com.therapyCommunity_Vol1.backend.knowledge.repository.KnowledgeChunkRepository;
import com.therapyCommunity_Vol1.backend.knowledge.repository.KnowledgeDocumentArtifactRepository;
import com.therapyCommunity_Vol1.backend.knowledge.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestionService {

    private static final int MAX_CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 200;
    private static final int MAX_POLL = 10;
    private static final int[] BACKOFF_MINUTES = {1, 5, 30};

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeDocumentArtifactRepository artifactRepository;
    private final List<DocumentExtractor> extractors;
    private final GeminiEmbeddingClient embeddingClient;
    private final FileStorageService fileStorageService;
    private final KnowledgeProperties properties;
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void pollDueDocuments() {
        if (!properties.isEnabled()) return;

        List<KnowledgeDocument> dueDocuments = documentRepository.findDueDocuments(LocalDateTime.now(), MAX_POLL);
        for (KnowledgeDocument doc : dueDocuments) {
            try {
                processDocument(doc);
            } catch (Exception e) {
                log.error("Knowledge ingestion failed: docId={}", doc.getId(), e);
            }
        }
    }

    @Transactional
    public void processDocument(KnowledgeDocument document) {
        document.markProcessing();

        try {
            DocumentExtractor extractor = extractors.stream()
                    .filter(e -> e.supports(document.getContentType()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No extractor for: " + document.getContentType()));

            InputStream fileStream = fileStorageService.loadAsStream(document.getStoredPath());
            ExtractedDocument extracted = extractor.extract(fileStream, document.getContentType());

            if (extracted.getPlainText() == null || extracted.getPlainText().isBlank()) {
                document.markFailed("EMPTY_EXTRACTION", "Extracted text is empty", null);
                return;
            }

            // 기존 chunk/artifact 삭제 (재인덱싱 대비)
            chunkRepository.deleteByDocumentId(document.getId());
            artifactRepository.deleteByDocumentId(document.getId());

            // artifact 저장
            artifactRepository.save(KnowledgeDocumentArtifact.create(
                    document, ArtifactType.PLAIN_TEXT, extracted.getPlainText(), null));
            if (extracted.getNormalizedHtml() != null) {
                artifactRepository.save(KnowledgeDocumentArtifact.create(
                        document, ArtifactType.NORMALIZED_HTML, extracted.getNormalizedHtml(), null));
            }

            // chunk 분할 + 임베딩
            List<String> chunks = splitChunks(extracted.getPlainText());
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                float[] embedding = embeddingClient.embed(chunkText);

                KnowledgeChunk chunk = KnowledgeChunk.create(
                        document, i, chunkText, chunkText.length(),
                        properties.getEmbeddingModel(), null
                );
                KnowledgeChunk saved = chunkRepository.save(chunk);

                // native SQL로 embedding 업데이트 (pgvector)
                jdbcTemplate.update(
                        "UPDATE knowledge_chunks SET embedding = ?::vector WHERE id = ?",
                        toVectorString(embedding), saved.getId()
                );
            }

            document.markReady();
        } catch (Exception e) {
            handleFailure(document, e);
        }
    }

    private void handleFailure(KnowledgeDocument document, Exception e) {
        String errorCode = e.getClass().getSimpleName();
        String errorMessage = e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "Unknown";

        if (document.isRetryable()) {
            int backoffIndex = Math.min(document.getAttemptCount() - 1, BACKOFF_MINUTES.length - 1);
            LocalDateTime nextAttempt = LocalDateTime.now().plusMinutes(BACKOFF_MINUTES[backoffIndex]);
            document.markFailed(errorCode, errorMessage, nextAttempt);
            document.retry();
        } else {
            document.markFailed(errorCode, errorMessage, null);
        }
    }

    private List<String> splitChunks(String text) {
        List<String> chunks = new java.util.ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHUNK_SIZE, text.length());
            chunks.add(text.substring(start, end));
            start += MAX_CHUNK_SIZE - CHUNK_OVERLAP;
        }
        return chunks;
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
