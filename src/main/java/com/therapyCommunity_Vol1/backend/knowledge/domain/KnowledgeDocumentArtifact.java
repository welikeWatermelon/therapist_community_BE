package com.therapyCommunity_Vol1.backend.knowledge.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_document_artifacts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeDocumentArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocument document;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, length = 50)
    private ArtifactType artifactType;

    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "content_json", columnDefinition = "JSONB")
    private String contentJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static KnowledgeDocumentArtifact create(
            KnowledgeDocument document,
            ArtifactType artifactType,
            String contentText,
            String contentJson
    ) {
        KnowledgeDocumentArtifact artifact = new KnowledgeDocumentArtifact();
        artifact.document = document;
        artifact.artifactType = artifactType;
        artifact.contentText = contentText;
        artifact.contentJson = contentJson;
        artifact.createdAt = LocalDateTime.now();
        return artifact;
    }
}
