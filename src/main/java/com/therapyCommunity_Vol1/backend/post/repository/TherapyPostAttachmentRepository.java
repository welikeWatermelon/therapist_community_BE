package com.therapyCommunity_Vol1.backend.post.repository;

import com.therapyCommunity_Vol1.backend.post.domain.TherapyPostAttachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TherapyPostAttachmentRepository extends JpaRepository<TherapyPostAttachment, Long> {

    List<TherapyPostAttachment> findByPostIdOrderByCreatedAtAsc(Long postId);

    @EntityGraph(attributePaths = "post")
    Optional<TherapyPostAttachment> findByIdAndPostId(Long attachmentId, Long postId);
}
