package com.therapyCommunity_Vol1.backend.scrap.repository;

import com.therapyCommunity_Vol1.backend.scrap.domain.TherapyPostScrap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.therapyCommunity_Vol1.backend.post.domain.Visibility;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TherapyPostScrapRepository extends JpaRepository<TherapyPostScrap, Long> {

    Optional<TherapyPostScrap> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    @EntityGraph(attributePaths = {"post", "post.author"})
    Page<TherapyPostScrap> findByUserIdAndPost_DeletedAtIsNull(Long userId, Pageable pageable);

    @Query("SELECT s.post.id FROM TherapyPostScrap s WHERE s.user.id = :userId AND s.post.id IN :postIds")
    Set<Long> findScrappedPostIdsByUserIdAndPostIdIn(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);

    @EntityGraph(attributePaths = {"post", "post.author"})
    Page<TherapyPostScrap> findByUserIdAndPost_DeletedAtIsNullAndPost_Visibility(Long userId, Visibility visibility, Pageable pageable);
}
