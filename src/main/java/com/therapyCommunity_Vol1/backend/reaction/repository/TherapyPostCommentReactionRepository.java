package com.therapyCommunity_Vol1.backend.reaction.repository;

import com.therapyCommunity_Vol1.backend.reaction.domain.CommentReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostCommentReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TherapyPostCommentReactionRepository extends JpaRepository<TherapyPostCommentReaction, Long> {

    Optional<TherapyPostCommentReaction> findByCommentIdAndUserId(Long commentId, Long userId);

    long countByCommentIdAndReactionType(Long commentId, CommentReactionType reactionType);

    /**
     * 댓글 목록 응답 빌드 시 N+1 제거용 batch 조회.
     * 호출처가 commentIds + currentUserId로 메모리에서 그룹화 후 카운트/내 reaction을 매핑.
     */
    java.util.List<TherapyPostCommentReaction> findByCommentIdIn(java.util.List<Long> commentIds);
}
