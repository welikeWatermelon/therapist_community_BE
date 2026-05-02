package com.therapyCommunity_Vol1.backend.comment.service;

import com.therapyCommunity_Vol1.backend.analytics.domain.EventTargetType;
import com.therapyCommunity_Vol1.backend.analytics.domain.UserEventType;
import com.therapyCommunity_Vol1.backend.analytics.event.UserEventPublisher;
import com.therapyCommunity_Vol1.backend.autocomment.config.AiCommentProperties;
import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.comment.dto.CommentReactionAggregate;
import com.therapyCommunity_Vol1.backend.comment.dto.CommentResponse;
import com.therapyCommunity_Vol1.backend.comment.dto.CreateCommentRequest;
import com.therapyCommunity_Vol1.backend.comment.dto.UpdateCommentRequest;
import com.therapyCommunity_Vol1.backend.comment.repository.TherapyPostCommentRepository;
import com.therapyCommunity_Vol1.backend.reaction.domain.CommentReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostCommentReaction;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostCommentReactionRepository;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import com.therapyCommunity_Vol1.backend.notification.event.NotificationEvent;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.service.ActivePostFinder;
import com.therapyCommunity_Vol1.backend.post.service.PostVisibilityAccessPolicy;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final TherapyPostCommentRepository commentRepository;
    private final ActivePostFinder activePostFinder;
    private final UserRepository userRepository;
    private final ResourceAccessValidator resourceAccessValidator;
    private final CommentThreadAssembler commentThreadAssembler;
    private final AiCommentProperties aiCommentProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final PostVisibilityAccessPolicy visibilityPolicy;
    private final UserEventPublisher userEventPublisher;
    private final TherapyPostCommentReactionRepository commentReactionRepository;

    @Transactional
    public CommentResponse createComment(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId,
            CreateCommentRequest request
    ) {
        User author = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole);

        TherapyPostComment comment;

        if (request.getParentCommentId() == null) {
            comment = TherapyPostComment.createRoot(
                    post,
                    author,
                    request.getContent()
            );
        } else {
            TherapyPostComment parentComment = commentRepository.findByIdAndDeletedAtIsNull(request.getParentCommentId())
                    .orElseThrow(() -> new CustomException(ErrorCode.INVALID_PARENT_COMMENT));

            if (!parentComment.getPost().getId().equals(postId)) {
                throw new CustomException(ErrorCode.INVALID_PARENT_COMMENT);
            }
            if (parentComment.isReply()) {
                throw new CustomException(ErrorCode.COMMENT_DEPTH_NOT_ALLOWED);
            }

            comment = TherapyPostComment.createReply(
                    post,
                    author,
                    parentComment,
                    request.getContent()
            );
        }

        TherapyPostComment saved = commentRepository.save(comment);

        if (request.getParentCommentId() == null) {
            eventPublisher.publishEvent(NotificationEvent.of(
                    currentUserId, post.getAuthor().getId(),
                    NotificationType.NEW_COMMENT, postId));
        } else {
            eventPublisher.publishEvent(NotificationEvent.of(
                    currentUserId, comment.getParentComment().getAuthor().getId(),
                    NotificationType.NEW_REPLY, postId));
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("commentId", saved.getId());
        metadata.put("isReply", request.getParentCommentId() != null);
        if (request.getParentCommentId() != null) {
            metadata.put("parentCommentId", request.getParentCommentId());
        }
        userEventPublisher.publish(
                currentUserId,
                UserEventType.COMMENT_CREATE,
                EventTargetType.POST,
                postId,
                metadata
        );

        // 새로 생성된 댓글이라 reaction 0개 + null
        return CommentResponse.from(saved, currentUserId, author.getRole(), aiCommentProperties.getAiUserEmail(), CommentReactionAggregate.empty());
    }

    public List<CommentResponse> getComments(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId
    ) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole);

        List<TherapyPostComment> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId);
        // N+1 제거: commentIds 한 번의 IN 절 SQL로 모든 reaction 가져와 메모리에서 매핑
        Map<Long, CommentReactionAggregate> reactionByCommentId = aggregateReactionsForComments(comments, currentUserId);
        return commentThreadAssembler.assemble(comments, currentUserId, currentUserRole, reactionByCommentId);
    }

    @Transactional
    public CommentResponse updateComment(
            Long currentUserId,
            UserRole currentUserRole,
            Long commentId,
            UpdateCommentRequest request
    ) {
        TherapyPostComment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
        visibilityPolicy.checkAccess(comment.getPost(), currentUserRole);

        resourceAccessValidator.validateAuthorOrAdmin(comment.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.COMMENT_ACCESS_DENIED);

        comment.update(request.getContent());

        // 단일 댓글 — 기존 reaction 그대로 (수정해도 좋아요는 유지)
        CommentReactionAggregate reactions = aggregateSingleCommentReactions(commentId, currentUserId);
        return CommentResponse.from(comment, currentUserId, currentUserRole, aiCommentProperties.getAiUserEmail(), reactions);
    }

    /**
     * 댓글 목록 응답 빌드 시점의 N+1 제거용 batch 헬퍼.
     * commentIds로 한 번의 IN 절 SQL → 메모리에서 (likeCount/curiousCount/usefulCount/myReactionType) 그룹화.
     */
    private Map<Long, CommentReactionAggregate> aggregateReactionsForComments(List<TherapyPostComment> comments, Long currentUserId) {
        if (comments.isEmpty()) {
            return Map.of();
        }
        List<Long> commentIds = comments.stream().map(TherapyPostComment::getId).toList();
        List<TherapyPostCommentReaction> reactions = commentReactionRepository.findByCommentIdIn(commentIds);
        Map<Long, CommentReactionAggregate> result = new HashMap<>();
        for (Long id : commentIds) {
            result.put(id, CommentReactionAggregate.empty());
        }
        Map<Long, List<TherapyPostCommentReaction>> grouped = new HashMap<>();
        for (TherapyPostCommentReaction r : reactions) {
            grouped.computeIfAbsent(r.getComment().getId(), ignored -> new java.util.ArrayList<>()).add(r);
        }
        for (Map.Entry<Long, List<TherapyPostCommentReaction>> entry : grouped.entrySet()) {
            long likes = 0;
            long curious = 0;
            long useful = 0;
            CommentReactionType my = null;
            for (TherapyPostCommentReaction r : entry.getValue()) {
                switch (r.getReactionType()) {
                    case LIKE -> likes++;
                    case CURIOUS -> curious++;
                    case USEFUL -> useful++;
                }
                if (r.getUser().getId().equals(currentUserId)) my = r.getReactionType();
            }
            result.put(entry.getKey(), new CommentReactionAggregate(likes, curious, useful, my));
        }
        return result;
    }

    private CommentReactionAggregate aggregateSingleCommentReactions(Long commentId, Long currentUserId) {
        long likes = commentReactionRepository.countByCommentIdAndReactionType(commentId, CommentReactionType.LIKE);
        long curious = commentReactionRepository.countByCommentIdAndReactionType(commentId, CommentReactionType.CURIOUS);
        long useful = commentReactionRepository.countByCommentIdAndReactionType(commentId, CommentReactionType.USEFUL);
        CommentReactionType my = commentReactionRepository.findByCommentIdAndUserId(commentId, currentUserId)
                .map(TherapyPostCommentReaction::getReactionType)
                .orElse(null);
        return new CommentReactionAggregate(likes, curious, useful, my);
    }

    @Transactional
    public void deleteComment(
            Long currentUserId,
            UserRole currentUserRole,
            Long commentId
    ) {
        TherapyPostComment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
        visibilityPolicy.checkAccess(comment.getPost(), currentUserRole);
        resourceAccessValidator.validateAuthorOrAdmin(comment.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.COMMENT_ACCESS_DENIED);

        comment.softDelete();
    }

    public TherapyPostComment findActiveComment(Long commentId) {
        return commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
    }

    public Page<TherapyPostComment> getMyComments(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return commentRepository.findByAuthorId(userId, pageable);
    }
}
