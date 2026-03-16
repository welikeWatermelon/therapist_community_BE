package com.therapyCommunity_Vol1.backend.reaction.repository;

import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostReaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TherapyPostReactionRepository extends JpaRepository<TherapyPostReaction, Long> {

    Optional<TherapyPostReaction> findByPostIdAndUserId(Long postId, Long userId);

    long countByPostIdAndReactionType(Long postId, PostReactionType reactionType);
}
