package com.therapyCommunity_Vol1.backend.reaction.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.notification.service.NotificationService;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
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
    private final TherapyPostRepository therapyPostRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public PostReactionStatusResponse toggleReaction(
            Long currentUserId,
            Long postId,
            TogglePostReactionRequest request
    ) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        TherapyPost post = therapyPostRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

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
                    // 새 반응 생성 시 게시글 작성자에게 알림
                    notificationService.createPostReactionNotification(
                            post.getAuthor(), user, postId, request.getReactionType().getLabel());
                });
        return getReactionStatus(currentUserId, postId);
    }

    public PostReactionStatusResponse getReactionStatus(
            Long currentUserId,
            Long postId
    ) {
        therapyPostRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

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

