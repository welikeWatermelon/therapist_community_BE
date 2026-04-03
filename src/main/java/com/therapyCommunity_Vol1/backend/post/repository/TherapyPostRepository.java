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

import java.util.Optional;

public interface TherapyPostRepository extends JpaRepository<TherapyPost, Long> {
    @EntityGraph(attributePaths = "author")
    Page<TherapyPost> findByDeletedAtIsNull(Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Optional<TherapyPost> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = "author")
    Page<TherapyPost> findByAuthorIdAndDeletedAtIsNull(Long authorId, Pageable pageable);

    // 초성 검색 (ㅇㅇㅊㄹ → 언어치료)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
              AND p.titleChoseong LIKE CONCAT('%', :choseong, '%') ESCAPE '\\'
              AND (:therapyArea IS NULL OR p.therapyArea = :therapyArea)
              AND (:postType IS NULL OR p.postType = :postType)
            """)
    Page<TherapyPost> searchByChoseong(
            @Param("choseong") String choseong,
            @Param("therapyArea") TherapyArea therapyArea,
            @Param("postType") PostType postType,
            Pageable pageable
    );

    // 일반 텍스트 검색 (title OR content 대소문자 무시 LIKE)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
              AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                   OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')
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
}
