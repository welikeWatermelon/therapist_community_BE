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

import java.util.List;
import java.util.Optional;

public interface TherapyPostRepository extends JpaRepository<TherapyPost, Long> {
    @EntityGraph(attributePaths = "author")
    Page<TherapyPost> findByDeletedAtIsNull(Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Optional<TherapyPost> findByIdAndDeletedAtIsNull(Long id);

    // keyword 검색: Step 1 — native query로 ID + 페이징만 조회
    @Query(value = """
            SELECT p.id FROM therapy_posts p
            WHERE p.deleted_at IS NULL
              AND p.search_vector @@ plainto_tsquery('simple', :keyword)
              AND (:therapyArea IS NULL OR p.therapy_area = :therapyArea)
              AND (:postType IS NULL OR p.post_type = :postType)
            ORDER BY
              ts_rank(p.search_vector, plainto_tsquery('simple', :keyword)) DESC,
              p.created_at DESC, p.id DESC
            """,
            countQuery = """
            SELECT count(*) FROM therapy_posts p
            WHERE p.deleted_at IS NULL
              AND p.search_vector @@ plainto_tsquery('simple', :keyword)
              AND (:therapyArea IS NULL OR p.therapy_area = :therapyArea)
              AND (:postType IS NULL OR p.post_type = :postType)
            """,
            nativeQuery = true)
    Page<Long> searchIdsByKeyword(
            @Param("keyword") String keyword,
            @Param("therapyArea") String therapyArea,
            @Param("postType") String postType,
            Pageable pageable
    );

    // keyword 검색: Step 2 — ID 목록으로 엔티티 + author 한번에 fetch
    @EntityGraph(attributePaths = "author")
    @Query("SELECT p FROM TherapyPost p WHERE p.id IN :ids")
    List<TherapyPost> findAllWithAuthorByIdIn(@Param("ids") List<Long> ids);

    // 필터만 (keyword 없음): JPQL + EntityGraph
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
}
