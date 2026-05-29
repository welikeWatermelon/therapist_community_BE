package com.therapyCommunity_Vol1.backend.reaction.service;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.comment.service.CommentService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import com.therapyCommunity_Vol1.backend.notification.event.NotificationEvent;
import com.therapyCommunity_Vol1.backend.comment.dto.CommentReactionAggregate;
import com.therapyCommunity_Vol1.backend.reaction.domain.CommentReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostCommentReaction;
import com.therapyCommunity_Vol1.backend.reaction.dto.CommentReactionStatusResponse;
import com.therapyCommunity_Vol1.backend.reaction.dto.ToggleCommentReactionRequest;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostCommentReactionRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CommentReactionService {

    private final CommentService commentService;
    private final UserService userService;
    private final TherapyPostCommentReactionRepository commentReactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CommentReactionStatusResponse toggleReaction(
            Long currentUserId,
            Long commentId,
            ToggleCommentReactionRequest request
    ) {
        User user = userService.findById(currentUserId);

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

    /**
     * 다건 댓글의 반응 집계를 배치 조회한다.
     */
    public Map<Long, CommentReactionAggregate> getReactionAggregatesByCommentIds(List<Long> commentIds, Long currentUserId) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        List<TherapyPostCommentReaction> reactions = commentReactionRepository.findByCommentIdIn(commentIds);

        // commentId별로 카운트와 myReaction을 집계
        Map<Long, long[]> counts = new HashMap<>(); // [like, curious, useful]
        Map<Long, CommentReactionType> myReactions = new HashMap<>();
        for (Long id : commentIds) {
            counts.put(id, new long[3]);
        }
        for (TherapyPostCommentReaction r : reactions) {
            Long cId = r.getComment().getId();
            long[] c = counts.get(cId);
            if (c != null) {
                switch (r.getReactionType()) {
                    case LIKE -> c[0]++;
                    case CURIOUS -> c[1]++;
                    case USEFUL -> c[2]++;
                }
                if (currentUserId != null && currentUserId.equals(r.getUser().getId())) {
                    myReactions.put(cId, r.getReactionType());
                }
            }
        }

        Map<Long, CommentReactionAggregate> result = new HashMap<>();
        for (Long id : commentIds) {
            long[] c = counts.get(id);
            result.put(id, new CommentReactionAggregate(c[0], c[1], c[2], myReactions.get(id)));
        }
        return result;
    }

    /**
     * 단건 댓글의 반응 집계를 조회한다.
     */
    public CommentReactionAggregate getReactionAggregate(Long commentId, Long currentUserId) {
        long likes = commentReactionRepository.countByCommentIdAndReactionType(commentId, CommentReactionType.LIKE);
        long curious = commentReactionRepository.countByCommentIdAndReactionType(commentId, CommentReactionType.CURIOUS);
        long useful = commentReactionRepository.countByCommentIdAndReactionType(commentId, CommentReactionType.USEFUL);
        CommentReactionType my = commentReactionRepository.findByCommentIdAndUserId(commentId, currentUserId)
                .map(TherapyPostCommentReaction::getReactionType)
                .orElse(null);
        return new CommentReactionAggregate(likes, curious, useful, my);
    }
}
