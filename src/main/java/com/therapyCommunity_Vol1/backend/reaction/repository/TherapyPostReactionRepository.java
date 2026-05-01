package com.therapyCommunity_Vol1.backend.reaction.repository;

import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TherapyPostReactionRepository extends JpaRepository<TherapyPostReaction, Long> {

    Optional<TherapyPostReaction> findByPostIdAndUserId(Long postId, Long userId);

    /** 기존 호환용 — 단일 타입 count. 향후 제거 가능. */
    long countByPostIdAndReactionType(Long postId, PostReactionType reactionType);

    /**
     * 게시글의 반응 타입별 count를 한 번의 GROUP BY 쿼리로 조회.
     * 반환: List<Object[]> — 각 원소는 [PostReactionType, Long(count)]
     *
     * 반응 타입이 추가되어도 이 쿼리는 수정 불필요.
     * count가 0인 타입은 결과에 포함되지 않으므로 서비스에서 보정 필요.
     */
    @Query("SELECT r.reactionType, COUNT(r) FROM TherapyPostReaction r " +
            "WHERE r.post.id = :postId GROUP BY r.reactionType")
    List<Object[]> countGroupedByPostId(@Param("postId") Long postId);

    /**
     * 여러 게시글에 대한 특정 반응 타입 count를 1회 GROUP BY 쿼리로 조회.
     * 반환: List<Object[]> — 각 원소는 [Long(postId), Long(count)]
     * 결과에 없는 postId는 count 0으로 간주 (서비스에서 보정).
     */
    @Query("SELECT r.post.id, COUNT(r) FROM TherapyPostReaction r " +
            "WHERE r.post.id IN :postIds AND r.reactionType = :reactionType " +
            "GROUP BY r.post.id")
    List<Object[]> countByPostIdInAndReactionType(
            @Param("postIds") List<Long> postIds,
            @Param("reactionType") PostReactionType reactionType
    );

    /**
     * 여러 게시글에 대한 모든 반응 타입 count를 1회 GROUP BY 쿼리로 조회.
     * 반환: List<Object[]> — 각 원소는 [Long(postId), PostReactionType, Long(count)]
     * 결과에 없는 (postId, type) 조합은 count 0.
     */
    @Query("SELECT r.post.id, r.reactionType, COUNT(r) FROM TherapyPostReaction r " +
            "WHERE r.post.id IN :postIds " +
            "GROUP BY r.post.id, r.reactionType")
    List<Object[]> countByPostIdInGroupedByType(@Param("postIds") List<Long> postIds);

    /**
     * 여러 게시글 중 현재 사용자의 반응만 batch로 조회 — myReactionType 매핑용.
     */
    List<TherapyPostReaction> findByPostIdInAndUserId(List<Long> postIds, Long userId);
}
