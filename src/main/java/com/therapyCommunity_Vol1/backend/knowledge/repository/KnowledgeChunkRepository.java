package com.therapyCommunity_Vol1.backend.knowledge.repository;

import com.therapyCommunity_Vol1.backend.knowledge.domain.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    void deleteByDocumentId(Long documentId);

    List<KnowledgeChunk> findByDocumentIdOrderByChunkIndex(Long documentId);
}
