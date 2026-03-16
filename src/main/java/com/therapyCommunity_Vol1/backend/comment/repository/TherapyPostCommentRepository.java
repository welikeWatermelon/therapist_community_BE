package com.therapyCommunity_Vol1.backend.comment.repository;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TherapyPostCommentRepository extends JpaRepository<TherapyPostComment, Long> {
    @EntityGraph(attributePaths = {"author", "parentComment"})
    List<TherapyPostComment> findByPostIdOrderByCreatedAtAsc(Long postId);

    @EntityGraph(attributePaths = {"author", "parentComment", "post"})
    Optional<TherapyPostComment> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"author", "parentComment", "post"})
    Optional<TherapyPostComment> findById(Long id);
}


