package com.therapyCommunity_Vol1.backend.post.repository;

import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // 커서 피드 — 전체, 첫 페이지 (커서 없음)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<TherapyPost> findFeedLatest(Pageable pageable);

    // 커서 피드 — 전체, 다음 페이지 (커서 있음)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
              AND (p.createdAt < :cursorCreatedAt OR
                   (p.createdAt = :cursorCreatedAt AND p.id < :cursorId))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<TherapyPost> findFeedLatest(
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // 커서 피드 — visibility, 첫 페이지 (커서 없음)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
              AND p.visibility = :visibility
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<TherapyPost> findFeedLatestByVisibility(
            @Param("visibility") Visibility visibility,
            Pageable pageable
    );

    // 커서 피드 — visibility, 다음 페이지 (커서 있음)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
              AND p.visibility = :visibility
              AND (p.createdAt < :cursorCreatedAt OR
                   (p.createdAt = :cursorCreatedAt AND p.id < :cursorId))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<TherapyPost> findFeedLatestByVisibility(
            @Param("visibility") Visibility visibility,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // 인기순 커서 피드 — 전체, 첫 페이지 (커서 없음)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
            ORDER BY p.popularityScore DESC, p.id DESC
            """)
    List<TherapyPost> findFeedPopular(Pageable pageable);

    // 인기순 커서 피드 — 전체, 다음 페이지 (커서 있음)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
              AND (p.popularityScore < :cursorScore OR
                   (p.popularityScore = :cursorScore AND p.id < :cursorId))
            ORDER BY p.popularityScore DESC, p.id DESC
            """)
    List<TherapyPost> findFeedPopular(
            @Param("cursorScore") Long cursorScore,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // 인기순 커서 피드 — visibility, 첫 페이지 (커서 없음)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
              AND p.visibility = :visibility
            ORDER BY p.popularityScore DESC, p.id DESC
            """)
    List<TherapyPost> findFeedPopularByVisibility(
            @Param("visibility") Visibility visibility,
            Pageable pageable
    );

    // 인기순 커서 피드 — visibility, 다음 페이지 (커서 있음)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
              AND p.visibility = :visibility
              AND (p.popularityScore < :cursorScore OR
                   (p.popularityScore = :cursorScore AND p.id < :cursorId))
            ORDER BY p.popularityScore DESC, p.id DESC
            """)
    List<TherapyPost> findFeedPopularByVisibility(
            @Param("visibility") Visibility visibility,
            @Param("cursorScore") Long cursorScore,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // popularity_score 재계산 (반응/스크랩 토글 시 호출)
    // 공식: 반응수 * 30 + 스크랩수 * 20 + (created_at epoch초 / 8640)
    // 8640 = TherapyPost.TIME_SCORE_DIVISOR — 약 2.4시간마다 1점 자연 증가. V25 마이그레이션과 동일.
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE therapy_posts SET popularity_score =
                (SELECT COUNT(*) FROM therapy_post_reactions WHERE post_id = :postId) * 30
              + (SELECT COUNT(*) FROM therapy_post_scraps WHERE post_id = :postId) * 20
              + CAST(EXTRACT(EPOCH FROM created_at) / 8640 AS BIGINT)
            WHERE id = :postId
            """, nativeQuery = true)
    void recalculatePopularityScore(@Param("postId") Long postId);

    // RELEVANCE 검색 — pg_trgm similarity + ILIKE fallback, 커서 기반 무한스크롤 전용.
    // :keyword 는 raw (similarity 전용), :escapedKeyword 는 LIKE 메타문자 이스케이프된 값 (ILIKE 전용).
    // 반환은 (id, score) 두 컬럼의 Object[] — Service 에서 다음 커서 계산에 score 가 필요해서 함께 노출.
    // LIMIT 은 :limit 파라미터로 받아 hasNext 판별용 take+1 조회를 수행한다.

    // (a) visibility 필터 없음 — 첫 페이지
    @Query(
            value = """
                    SELECT p.id, similarity(p.search_text, :keyword) AS score
                    FROM therapy_posts p
                    WHERE p.deleted_at IS NULL
                      AND (CAST(:therapyArea AS text) IS NULL OR p.therapy_area = CAST(:therapyArea AS text))
                      AND (CAST(:postType AS text) IS NULL OR p.post_type = CAST(:postType AS text))
                      AND (
                            similarity(p.search_text, :keyword) > 0.03
                         OR p.search_text ILIKE '%' || :escapedKeyword || '%' ESCAPE '\\'
                      )
                    ORDER BY score DESC, p.id DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<Object[]> searchIdsByRelevanceFirstPage(
            @Param("keyword") String keyword,
            @Param("escapedKeyword") String escapedKeyword,
            @Param("therapyArea") String therapyArea,
            @Param("postType") String postType,
            @Param("limit") int limit
    );

    // (b) visibility 필터 없음 — 다음 페이지 (커서 조건 추가)
    @Query(
            value = """
                    SELECT p.id, similarity(p.search_text, :keyword) AS score
                    FROM therapy_posts p
                    WHERE p.deleted_at IS NULL
                      AND (CAST(:therapyArea AS text) IS NULL OR p.therapy_area = CAST(:therapyArea AS text))
                      AND (CAST(:postType AS text) IS NULL OR p.post_type = CAST(:postType AS text))
                      AND (
                            similarity(p.search_text, :keyword) > 0.03
                         OR p.search_text ILIKE '%' || :escapedKeyword || '%' ESCAPE '\\'
                      )
                      AND (
                            similarity(p.search_text, :keyword) < :lastScore
                         OR (similarity(p.search_text, :keyword) = :lastScore AND p.id < :lastId)
                      )
                    ORDER BY score DESC, p.id DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<Object[]> searchIdsByRelevanceNextPage(
            @Param("keyword") String keyword,
            @Param("escapedKeyword") String escapedKeyword,
            @Param("therapyArea") String therapyArea,
            @Param("postType") String postType,
            @Param("lastScore") double lastScore,
            @Param("lastId") long lastId,
            @Param("limit") int limit
    );

    // (c) visibility 필터 있음 — 첫 페이지 (USER → PUBLIC)
    @Query(
            value = """
                    SELECT p.id, similarity(p.search_text, :keyword) AS score
                    FROM therapy_posts p
                    WHERE p.deleted_at IS NULL
                      AND p.visibility = CAST(:visibility AS text)
                      AND (CAST(:therapyArea AS text) IS NULL OR p.therapy_area = CAST(:therapyArea AS text))
                      AND (CAST(:postType AS text) IS NULL OR p.post_type = CAST(:postType AS text))
                      AND (
                            similarity(p.search_text, :keyword) > 0.03
                         OR p.search_text ILIKE '%' || :escapedKeyword || '%' ESCAPE '\\'
                      )
                    ORDER BY score DESC, p.id DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<Object[]> searchIdsByRelevanceFirstPageAndVisibility(
            @Param("keyword") String keyword,
            @Param("escapedKeyword") String escapedKeyword,
            @Param("therapyArea") String therapyArea,
            @Param("postType") String postType,
            @Param("visibility") String visibility,
            @Param("limit") int limit
    );

    // (d) visibility 필터 있음 — 다음 페이지
    @Query(
            value = """
                    SELECT p.id, similarity(p.search_text, :keyword) AS score
                    FROM therapy_posts p
                    WHERE p.deleted_at IS NULL
                      AND p.visibility = CAST(:visibility AS text)
                      AND (CAST(:therapyArea AS text) IS NULL OR p.therapy_area = CAST(:therapyArea AS text))
                      AND (CAST(:postType AS text) IS NULL OR p.post_type = CAST(:postType AS text))
                      AND (
                            similarity(p.search_text, :keyword) > 0.03
                         OR p.search_text ILIKE '%' || :escapedKeyword || '%' ESCAPE '\\'
                      )
                      AND (
                            similarity(p.search_text, :keyword) < :lastScore
                         OR (similarity(p.search_text, :keyword) = :lastScore AND p.id < :lastId)
                      )
                    ORDER BY score DESC, p.id DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<Object[]> searchIdsByRelevanceNextPageAndVisibility(
            @Param("keyword") String keyword,
            @Param("escapedKeyword") String escapedKeyword,
            @Param("therapyArea") String therapyArea,
            @Param("postType") String postType,
            @Param("visibility") String visibility,
            @Param("lastScore") double lastScore,
            @Param("lastId") long lastId,
            @Param("limit") int limit
    );

    // ID 리스트로 author 까지 fetch (RELEVANCE 두 단계 fetch 의 두 번째 단계)
    @EntityGraph(attributePaths = "author")
    @Query("SELECT p FROM TherapyPost p WHERE p.id IN :ids")
    List<TherapyPost> findAllByIdInWithAuthor(@Param("ids") List<Long> ids);
}
