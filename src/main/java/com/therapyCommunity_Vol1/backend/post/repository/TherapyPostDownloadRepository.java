package com.therapyCommunity_Vol1.backend.post.repository;

import com.therapyCommunity_Vol1.backend.post.domain.TherapyPostDownload;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TherapyPostDownloadRepository extends JpaRepository<TherapyPostDownload, Long> {

    Optional<TherapyPostDownload> findByPostIdAndUserId(Long postId, Long userId);

    @EntityGraph(attributePaths = {"post", "post.author"})
    Page<TherapyPostDownload> findByUserIdAndPost_DeletedAtIsNull(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"post", "post.author"})
    Page<TherapyPostDownload> findByUserIdAndPost_DeletedAtIsNullAndPost_Visibility(Long userId, Visibility visibility, Pageable pageable);
}
