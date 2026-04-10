package com.therapyCommunity_Vol1.backend.knowledge.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_chunks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocument document;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    // embedding은 native SQL로 직접 관리 (pgvector)
    // JPA 엔티티에서는 매핑하지 않음

    @Column(name = "metadata_json", columnDefinition = "JSONB")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static KnowledgeChunk create(
            KnowledgeDocument document,
            int chunkIndex,
            String content,
            Integer tokenCount,
            String embeddingModel,
            String metadataJson
    ) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.document = document;
        chunk.chunkIndex = chunkIndex;
        chunk.content = content;
        chunk.tokenCount = tokenCount;
        chunk.embeddingModel = embeddingModel;
        chunk.metadataJson = metadataJson;
        chunk.createdAt = LocalDateTime.now();
        return chunk;
    }
}
