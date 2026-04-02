package com.therapyCommunity_Vol1.backend.scrap.repository;

import com.therapyCommunity_Vol1.backend.scrap.domain.TherapyPostScrap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TherapyPostScrapRepository extends JpaRepository<TherapyPostScrap, Long> {

    Optional<TherapyPostScrap> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    @EntityGraph(attributePaths = {"post", "post.author"})
    Page<TherapyPostScrap> findByUserIdAndPost_DeletedAtIsNull(Long userId, Pageable pageable);
}
