package com.therapyCommunity_Vol1.backend.reaction.service;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.comment.service.CommentService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import com.therapyCommunity_Vol1.backend.notification.event.NotificationEvent;
import com.therapyCommunity_Vol1.backend.reaction.domain.CommentReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostCommentReaction;
import com.therapyCommunity_Vol1.backend.reaction.dto.CommentReactionStatusResponse;
import com.therapyCommunity_Vol1.backend.reaction.dto.ToggleCommentReactionRequest;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostCommentReactionRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CommentReactionService {

    private final CommentService commentService;
    private final UserRepository userRepository;
    private final TherapyPostCommentReactionRepository commentReactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CommentReactionStatusResponse toggleReaction(
            Long currentUserId,
            Long commentId,
            ToggleCommentReactionRequest request
    ) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        TherapyPostComment comment = commentService.findActiveComment(commentId);

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

                    eventPublisher.publishEvent(NotificationEvent.of(
                            currentUserId, comment.getAuthor().getId(),
                            NotificationType.NEW_COMMENT_REACTION, commentId,
                            comment.getPost().getId(),
                            request.getReactionType().getLabel()));
                });
        return getReactionStatus(currentUserId, commentId);
    }

    public CommentReactionStatusResponse getReactionStatus(
            Long currentUserId,
            Long commentId
    ) {
        commentService.findActiveComment(commentId);

        CommentReactionType myReactionType = commentReactionRepository.findByCommentIdAndUserId(
                        commentId, currentUserId)
                .map(TherapyPostCommentReaction::getReactionType)
                .orElse(null);

        long likeCount = commentReactionRepository.countByCommentIdAndReactionType(commentId, CommentReactionType.LIKE);
        long curiousCount = commentReactionRepository.countByCommentIdAndReactionType(commentId, CommentReactionType.CURIOUS);
        long usefulCount = commentReactionRepository.countByCommentIdAndReactionType(commentId, CommentReactionType.USEFUL);

        return new CommentReactionStatusResponse(
                commentId,
                likeCount,
                curiousCount,
                usefulCount,
                myReactionType
        );
    }
}
