package com.therapyCommunity_Vol1.backend.reaction.service;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.comment.repository.TherapyPostCommentRepository;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.notification.service.NotificationService;
import com.therapyCommunity_Vol1.backend.reaction.domain.CommentReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostCommentReaction;
import com.therapyCommunity_Vol1.backend.reaction.dto.CommentReactionStatusResponse;
import com.therapyCommunity_Vol1.backend.reaction.dto.ToggleCommentReactionRequest;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostCommentReactionRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CommentReactionService {

    private final TherapyPostCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final TherapyPostCommentReactionRepository commentReactionRepository;
    private final NotificationService notificationService;

    @Transactional
    public CommentReactionStatusResponse toggleReaction(
            Long currentUserId,
            Long commentId,
            ToggleCommentReactionRequest request
    ) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        TherapyPostComment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

        commentReactionRepository.findByCommentIdAndUserId(commentId, currentUserId)
                .ifPresentOrElse(exsisting -> {
                    if (exsisting.getReactionType() == request.getReactionType()) {
                        commentReactionRepository.delete(exsisting);
                    } else {
                        exsisting.changeReactionType(request.getReactionType());
                    }
                }, () -> {
                    TherapyPostCommentReaction reaction = TherapyPostCommentReaction.create(
                            comment,
                            user,
                            request.getReactionType()
                    );
                    commentReactionRepository.save(reaction);
                    // LIKE 반응만 알림 생성 (DISLIKE 제외)
                    if (request.getReactionType() == CommentReactionType.LIKE) {
                        notificationService.createCommentReactionNotification(
                                comment.getAuthor(), user, commentId);
                    }
                });
        return getReactionStatus(currentUserId, commentId);
    }

    public CommentReactionStatusResponse getReactionStatus(
            Long currentUserId,
            Long commentId
    ) {
        commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));

        CommentReactionType myReactionType = commentReactionRepository.findByCommentIdAndUserId(
                        commentId, currentUserId)
                .map(TherapyPostCommentReaction::getReactionType)
                .orElse(null);

        long likeCount = commentReactionRepository.countByCommentIdAndReactionType(commentId, CommentReactionType.LIKE);
        long dislikeCount = commentReactionRepository.countByCommentIdAndReactionType(commentId, CommentReactionType.DISLIKE);

        return new CommentReactionStatusResponse(
                commentId,
                likeCount,
                dislikeCount,
                myReactionType
        );
    }
}
