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

import java.math.BigDecimal;
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

    // RELEVANCE 검색 — pg_trgm % 연산자 + ILIKE fallback, 커서 기반 무한스크롤 전용.
    //
    // 주요 설계:
    // - 매칭 술어: `search_text % :keyword` (gin_trgm_ops 가 직접 지원하는 연산자) 와
    //   `ILIKE` 양쪽을 OR 로 묶는다. 둘 다 idx_therapy_posts_search_text_trgm GIN 인덱스를
    //   사용해 BitmapOr 로 후보를 좁힌다. 임계값은 호출자가 SET LOCAL pg_trgm.similarity_threshold
    //   로 미리 지정한다 (현재 0.03). similarity(...) > 0.03 함수 호출 형태는 GIN 인덱스를
    //   못 타기 때문에 % 연산자로 교체했다.
    // - 점수 컬럼: similarity() 결과(real/float4) 를 numeric(10,8) 로 캐스트해 노출한다.
    //   응답 BigDecimal → 클라이언트 → 다음 요청 BigDecimal 왕복에서 정밀도 손실이 없어
    //   동등 비교(=) 가 안전하다.
    // - 커서 조건: 동일하게 numeric(10,8) 캐스트한 값과 :lastScore 를 비교한다.
    // - LIMIT 은 :limit 파라미터로 받아 hasNext 판별용 take+1 조회를 수행한다.

    // (a) visibility 필터 없음 — 첫 페이지
    @Query(
            value = """
                    SELECT p.id, CAST(similarity(p.search_text, :keyword) AS numeric(10,8)) AS score
                    FROM therapy_posts p
                    WHERE p.deleted_at IS NULL
                      AND (CAST(:therapyArea AS text) IS NULL OR p.therapy_area = CAST(:therapyArea AS text))
                      AND (CAST(:postType AS text) IS NULL OR p.post_type = CAST(:postType AS text))
                      AND (
                            p.search_text % :keyword
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
                    SELECT p.id, CAST(similarity(p.search_text, :keyword) AS numeric(10,8)) AS score
                    FROM therapy_posts p
                    WHERE p.deleted_at IS NULL
                      AND (CAST(:therapyArea AS text) IS NULL OR p.therapy_area = CAST(:therapyArea AS text))
                      AND (CAST(:postType AS text) IS NULL OR p.post_type = CAST(:postType AS text))
                      AND (
                            p.search_text % :keyword
                         OR p.search_text ILIKE '%' || :escapedKeyword || '%' ESCAPE '\\'
                      )
                      AND (
                            CAST(similarity(p.search_text, :keyword) AS numeric(10,8)) < :lastScore
                         OR (CAST(similarity(p.search_text, :keyword) AS numeric(10,8)) = :lastScore AND p.id < :lastId)
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
            @Param("lastScore") BigDecimal lastScore,
            @Param("lastId") long lastId,
            @Param("limit") int limit
    );

    // (c) visibility 필터 있음 — 첫 페이지 (USER → PUBLIC)
    @Query(
            value = """
                    SELECT p.id, CAST(similarity(p.search_text, :keyword) AS numeric(10,8)) AS score
                    FROM therapy_posts p
                    WHERE p.deleted_at IS NULL
                      AND p.visibility = CAST(:visibility AS text)
                      AND (CAST(:therapyArea AS text) IS NULL OR p.therapy_area = CAST(:therapyArea AS text))
                      AND (CAST(:postType AS text) IS NULL OR p.post_type = CAST(:postType AS text))
                      AND (
                            p.search_text % :keyword
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
                    SELECT p.id, CAST(similarity(p.search_text, :keyword) AS numeric(10,8)) AS score
                    FROM therapy_posts p
                    WHERE p.deleted_at IS NULL
                      AND p.visibility = CAST(:visibility AS text)
                      AND (CAST(:therapyArea AS text) IS NULL OR p.therapy_area = CAST(:therapyArea AS text))
                      AND (CAST(:postType AS text) IS NULL OR p.post_type = CAST(:postType AS text))
                      AND (
                            p.search_text % :keyword
                         OR p.search_text ILIKE '%' || :escapedKeyword || '%' ESCAPE '\\'
                      )
                      AND (
                            CAST(similarity(p.search_text, :keyword) AS numeric(10,8)) < :lastScore
                         OR (CAST(similarity(p.search_text, :keyword) AS numeric(10,8)) = :lastScore AND p.id < :lastId)
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
            @Param("lastScore") BigDecimal lastScore,
            @Param("lastId") long lastId,
            @Param("limit") int limit
    );

    // ID 리스트로 author 까지 fetch (RELEVANCE 두 단계 fetch 의 두 번째 단계)
    @EntityGraph(attributePaths = "author")
    @Query("SELECT p FROM TherapyPost p WHERE p.id IN :ids")
    List<TherapyPost> findAllByIdInWithAuthor(@Param("ids") List<Long> ids);
}
