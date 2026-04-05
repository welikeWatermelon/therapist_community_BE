package com.therapyCommunity_Vol1.backend.reaction.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.service.ActivePostFinder;
import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostReaction;
import com.therapyCommunity_Vol1.backend.reaction.dto.PostReactionStatusResponse;
import com.therapyCommunity_Vol1.backend.reaction.dto.TogglePostReactionRequest;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostReactionRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostReactionService {

    private final TherapyPostReactionRepository postReactionRepository;
    private final ActivePostFinder activePostFinder;
    private final UserRepository userRepository;

    @Transactional
    public PostReactionStatusResponse toggleReaction(
            Long currentUserId,
            Long postId,
            TogglePostReactionRequest request
    ) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        TherapyPost post = activePostFinder.findOrThrow(postId);

        postReactionRepository.findByPostIdAndUserId(postId, currentUserId)
                .ifPresentOrElse(existing -> {
                    if (existing.getReactionType() == request.getReactionType()) {
                        postReactionRepository.delete(existing);
                    } else {
                        existing.changeReactionType(request.getReactionType());
                    }
                }, () -> {
                    TherapyPostReaction reaction = TherapyPostReaction.create(
                            post,
                            user,
                            request.getReactionType()
                    );
                    postReactionRepository.save(reaction);
                });
        return getReactionStatus(currentUserId, postId);
    }

    public PostReactionStatusResponse getReactionStatus(
            Long currentUserId,
            Long postId
    ) {
        activePostFinder.findOrThrow(postId);

        PostReactionType myReactionType = postReactionRepository.findByPostIdAndUserId(postId, currentUserId)
                .map(TherapyPostReaction::getReactionType)
                .orElse(null);

        long empathyCount = postReactionRepository.countByPostIdAndReactionType(postId, PostReactionType.EMPATHY);
        long appreciateCount = postReactionRepository.countByPostIdAndReactionType(postId, PostReactionType.APPRECIATE);
        long helpfulCount = postReactionRepository.countByPostIdAndReactionType(postId, PostReactionType.HELPFUL);

        return new PostReactionStatusResponse(
                postId,
                empathyCount,
                appreciateCount,
                helpfulCount,
                myReactionType
        );
    }
}

