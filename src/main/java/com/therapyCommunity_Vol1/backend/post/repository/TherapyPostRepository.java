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

    // 텍스트 검색 (searchText LIKE — GIN trigram 인덱스 idx_therapy_posts_search_text_trgm 활용)
    // LOWER() 제거: GIN trigram 인덱스는 bare LIKE만 가속, LOWER() 래핑 시 Seq Scan.
    // 한글 위주 서비스라 대소문자 구분 문제 없음. 영문 edge case는 /search ILIKE가 커버.
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
              AND p.searchText LIKE CONCAT('%', :keyword, '%') ESCAPE '\\'
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

    // visibility 필터 포함 — 텍스트 검색 (searchText LIKE, LOWER 제거로 GIN 인덱스 활용)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p FROM TherapyPost p
            WHERE p.deletedAt IS NULL
              AND p.visibility = :visibility
              AND p.searchText LIKE CONCAT('%', :keyword, '%') ESCAPE '\\'
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

    // RELEVANCE 검색 — pg_trgm <% (word_similarity) 연산자 + ILIKE fallback, 커서 기반 무한스크롤 전용.
    //
    // 주요 설계:
    // - 매칭 술어: `:keyword <% search_text` (word_similarity 기반, gin_trgm_ops 지원) 와
    //   `ILIKE` 양쪽을 OR 로 묶는다. 둘 다 idx_therapy_posts_search_text_trgm GIN 인덱스를
    //   사용해 BitmapOr 로 후보를 좁힌다. 임계값은 호출자가
    //   SET LOCAL pg_trgm.word_similarity_threshold 로 미리 지정한다 (현재 0.1).
    // - <% vs %: search_text가 content(100자)+therapyArea 결합이라 keyword 대비 길므로, 짧은 keyword와의
    //   전체 문자열 similarity(%)는 극도로 낮아짐. word_similarity(<%)는 keyword가
    //   search_text 내 부분 단어와 유사한지를 평가하므로 더 적합함.
    // - 점수 컬럼: word_similarity() 결과(real/float4) 를 numeric(10,8) 로 캐스트해 노출한다.
    //   응답 BigDecimal → 클라이언트 → 다음 요청 BigDecimal 왕복에서 정밀도 손실이 없어
    //   동등 비교(=) 가 안전하다.
    // - 커서 조건: 동일하게 numeric(10,8) 캐스트한 값과 :lastScore 를 비교한다.
    // - LIMIT 은 :limit 파라미터로 받아 hasNext 판별용 take+1 조회를 수행한다.

    // (a) visibility 필터 없음 — 첫 페이지
    @Query(
            value = """
                    SELECT p.id, CAST(word_similarity(:keyword, p.search_text) AS numeric(10,8)) AS score
                    FROM therapy_posts p
                    WHERE p.deleted_at IS NULL
                      AND (CAST(:therapyArea AS text) IS NULL OR p.therapy_area = CAST(:therapyArea AS text))
                      AND (CAST(:postType AS text) IS NULL OR p.post_type = CAST(:postType AS text))
                      AND (
                            :keyword <% p.search_text
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
    // word_similarity()를 서브쿼리에서 1회만 계산하고, 외부에서 커서 필터링
    @Query(
            value = """
                    SELECT sub.id, sub.score
                    FROM (
                        SELECT p.id, CAST(word_similarity(:keyword, p.search_text) AS numeric(10,8)) AS score
                        FROM therapy_posts p
                        WHERE p.deleted_at IS NULL
                          AND (CAST(:therapyArea AS text) IS NULL OR p.therapy_area = CAST(:therapyArea AS text))
                          AND (CAST(:postType AS text) IS NULL OR p.post_type = CAST(:postType AS text))
                          AND (
                                :keyword <% p.search_text
                             OR p.search_text ILIKE '%' || :escapedKeyword || '%' ESCAPE '\\'
                          )
                    ) sub
                    WHERE sub.score < :lastScore
                       OR (sub.score = :lastScore AND sub.id < :lastId)
                    ORDER BY sub.score DESC, sub.id DESC
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
                    SELECT p.id, CAST(word_similarity(:keyword, p.search_text) AS numeric(10,8)) AS score
                    FROM therapy_posts p
                    WHERE p.deleted_at IS NULL
                      AND p.visibility = CAST(:visibility AS text)
                      AND (CAST(:therapyArea AS text) IS NULL OR p.therapy_area = CAST(:therapyArea AS text))
                      AND (CAST(:postType AS text) IS NULL OR p.post_type = CAST(:postType AS text))
                      AND (
                            :keyword <% p.search_text
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
    // word_similarity()를 서브쿼리에서 1회만 계산하고, 외부에서 커서 필터링
    @Query(
            value = """
                    SELECT sub.id, sub.score
                    FROM (
                        SELECT p.id, CAST(word_similarity(:keyword, p.search_text) AS numeric(10,8)) AS score
                        FROM therapy_posts p
                        WHERE p.deleted_at IS NULL
                          AND p.visibility = CAST(:visibility AS text)
                          AND (CAST(:therapyArea AS text) IS NULL OR p.therapy_area = CAST(:therapyArea AS text))
                          AND (CAST(:postType AS text) IS NULL OR p.post_type = CAST(:postType AS text))
                          AND (
                                :keyword <% p.search_text
                             OR p.search_text ILIKE '%' || :escapedKeyword || '%' ESCAPE '\\'
                          )
                    ) sub
                    WHERE sub.score < :lastScore
                       OR (sub.score = :lastScore AND sub.id < :lastId)
                    ORDER BY sub.score DESC, sub.id DESC
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
