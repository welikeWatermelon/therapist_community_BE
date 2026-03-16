package com.therapyCommunity_Vol1.backend.reaction.repository;

import com.therapyCommunity_Vol1.backend.reaction.domain.CommentReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostCommentReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TherapyPostCommentReactionRepository extends JpaRepository<TherapyPostCommentReaction, Long> {

    Optional<TherapyPostCommentReaction> findByCommentIdAndUserId(Long commentId, Long userId);

    long countByCommentIdAndReactionType(Long commentId, CommentReactionType reactionType);
}
