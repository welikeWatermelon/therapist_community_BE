package com.therapyCommunity_Vol1.backend.knowledge.repository;

import com.therapyCommunity_Vol1.backend.knowledge.domain.KnowledgeDocumentArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentArtifactRepository extends JpaRepository<KnowledgeDocumentArtifact, Long> {

    void deleteByDocumentId(Long documentId);
}
