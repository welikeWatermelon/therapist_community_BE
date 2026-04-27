package com.therapyCommunity_Vol1.backend.comment.repository;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** 단일 게시글의 활성 댓글 수 (soft-delete 제외). */
    long countByPostIdAndDeletedAtIsNull(Long postId);

    /**
     * 여러 게시글의 활성 댓글 수를 1회 GROUP BY 쿼리로 조회.
     * 반환: List<Object[]> — 각 원소는 [Long(postId), Long(count)]
     * 결과에 없는 postId는 count 0으로 간주.
     */
    @Query("SELECT c.post.id, COUNT(c) FROM TherapyPostComment c " +
            "WHERE c.post.id IN :postIds AND c.deletedAt IS NULL " +
            "GROUP BY c.post.id")
    List<Object[]> countActiveByPostIdIn(@Param("postIds") List<Long> postIds);
}

