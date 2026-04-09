package com.therapyCommunity_Vol1.backend.post.repository;

import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.therapyCommunity_Vol1.backend.post.domain.Visibility;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TherapyPostRepository extends JpaRepository<TherapyPost, Long> {
    @EntityGraph(attributePaths = "author")
    Page<TherapyPost> findByDeletedAtIsNull(Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Optional<TherapyPost> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = "author")
    Page<TherapyPost> findByAuthorIdAndDeletedAtIsNull(Long authorId, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Page<TherapyPost> findByDeletedAtIsNullAndVisibility(Visibility visibility, Pageable pageable);

    // 텍스트 검색 (content ILIKE)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
              AND LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
              AND (:therapyArea IS NULL OR p.therapyArea = :therapyArea)
              AND (:postType IS NULL OR p.postType = :postType)
            """)
    Page<TherapyPost> searchByKeyword(
            @Param("keyword") String keyword,
            @Param("therapyArea") TherapyArea therapyArea,
            @Param("postType") PostType postType,
            Pageable pageable
    );

    // 필터만 (keyword 없음)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
              AND (:therapyArea IS NULL OR p.therapyArea = :therapyArea)
              AND (:postType IS NULL OR p.postType = :postType)
            """)
    Page<TherapyPost> searchByFilter(
            @Param("therapyArea") TherapyArea therapyArea,
            @Param("postType") PostType postType,
            Pageable pageable
    );

    // visibility 필터 포함 — 텍스트 검색
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
              AND p.visibility = :visibility
              AND LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
              AND (:therapyArea IS NULL OR p.therapyArea = :therapyArea)
              AND (:postType IS NULL OR p.postType = :postType)
            """)
    Page<TherapyPost> searchByKeywordAndVisibility(
            @Param("keyword") String keyword,
            @Param("therapyArea") TherapyArea therapyArea,
            @Param("postType") PostType postType,
            @Param("visibility") Visibility visibility,
            Pageable pageable
    );

    // visibility 필터 포함 — 필터만
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
              AND p.visibility = :visibility
              AND (:therapyArea IS NULL OR p.therapyArea = :therapyArea)
              AND (:postType IS NULL OR p.postType = :postType)
            """)
    Page<TherapyPost> searchByFilterAndVisibility(
            @Param("therapyArea") TherapyArea therapyArea,
            @Param("postType") PostType postType,
            @Param("visibility") Visibility visibility,
            Pageable pageable
    );

    // 커서 피드 — 전체 (THERAPIST/ADMIN)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
              AND (:cursorCreatedAt IS NULL OR
                   p.createdAt < :cursorCreatedAt OR
                   (p.createdAt = :cursorCreatedAt AND p.id < :cursorId))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<TherapyPost> findFeedLatest(
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // 커서 피드 — visibility 필터 (USER → PUBLIC)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
              AND p.visibility = :visibility
              AND (:cursorCreatedAt IS NULL OR
                   p.createdAt < :cursorCreatedAt OR
                   (p.createdAt = :cursorCreatedAt AND p.id < :cursorId))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<TherapyPost> findFeedLatestByVisibility(
            @Param("visibility") Visibility visibility,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}
