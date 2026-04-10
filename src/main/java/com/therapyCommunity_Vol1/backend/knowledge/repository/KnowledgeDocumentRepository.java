package com.therapyCommunity_Vol1.backend.knowledge.repository;

import com.therapyCommunity_Vol1.backend.knowledge.domain.DocumentStatus;
import com.therapyCommunity_Vol1.backend.knowledge.domain.KnowledgeDocument;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    boolean existsByChecksum(String checksum);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM KnowledgeDocument d WHERE d.id = :id")
    Optional<KnowledgeDocument> findByIdForUpdate(@Param("id") Long id);

    Page<KnowledgeDocument> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(value = """
            SELECT * FROM knowledge_documents
            WHERE status = 'QUEUED'
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY next_attempt_at ASC NULLS FIRST
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<KnowledgeDocument> findDueDocuments(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
