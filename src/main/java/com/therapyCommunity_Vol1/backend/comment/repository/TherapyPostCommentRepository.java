package com.therapyCommunity_Vol1.backend.comment.repository;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
    Page<TherapyPostComment> findByAuthorIdOrderByCreatedAtDesc(Long authorId, Pageable pageable);
}

