package com.therapyCommunity_Vol1.backend.comment.repository;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TherapyPostCommentRepository extends JpaRepository<TherapyPostComment, Long> {
    @EntityGraph(attributePaths = {"author", "parentComment", "post"})
    List<TherapyPostComment> findByPostIdOrderByCreatedAtAsc(Long postId);

    @EntityGraph(attributePaths = {"author", "parentComment", "post"})
    Optional<TherapyPostComment> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"author", "parentComment", "post"})
    Optional<TherapyPostComment> findById(Long id);

    @EntityGraph(attributePaths = "post")
    Page<TherapyPostComment> findByAuthorId(Long authorId, Pageable pageable);

    // ===== Admin 통계용 =====

    long countByDeletedAtIsNull();

    @Query("SELECT COUNT(c) FROM TherapyPostComment c WHERE c.deletedAt IS NULL AND c.createdAt >= :since")
    long countActiveCommentsCreatedAfter(@Param("since") LocalDateTime since);

    long countByAuthorIdAndDeletedAtIsNull(Long authorId);

    // Admin 댓글 목록 — 삭제 댓글 포함, postId 옵션
    @EntityGraph(attributePaths = {"author", "parentComment", "post"})
    @Query("""
            SELECT c FROM TherapyPostComment c
            WHERE (:postId IS NULL OR c.post.id = :postId)
            """)
    Page<TherapyPostComment> adminSearch(@Param("postId") Long postId, Pageable pageable);
}

