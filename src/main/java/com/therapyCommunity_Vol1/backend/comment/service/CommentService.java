package com.therapyCommunity_Vol1.backend.comment.service;

import com.therapyCommunity_Vol1.backend.analytics.domain.EventTargetType;
import com.therapyCommunity_Vol1.backend.analytics.domain.UserEventType;
import com.therapyCommunity_Vol1.backend.analytics.event.UserEventPublisher;
import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.comment.dto.CommentResponse;
import com.therapyCommunity_Vol1.backend.comment.dto.CreateCommentRequest;
import com.therapyCommunity_Vol1.backend.comment.dto.UpdateCommentRequest;
import com.therapyCommunity_Vol1.backend.comment.repository.TherapyPostCommentRepository;
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
    private final ApplicationEventPublisher eventPublisher;
    private final PostVisibilityAccessPolicy visibilityPolicy;
    private final UserEventPublisher userEventPublisher;

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
            eventPublisher.publishEvent(NotificationEvent.builder()
                    .senderId(currentUserId)
                    .receiverIds(List.of(post.getAuthor().getId()))
                    .type(NotificationType.NEW_COMMENT)
                    .referenceId(postId)
                    .content(author.getNickname() + "님이 회원님의 게시글에 댓글을 남겼습니다.")
                    .build());
        } else {
            eventPublisher.publishEvent(NotificationEvent.builder()
                    .senderId(currentUserId)
                    .receiverIds(List.of(comment.getParentComment().getAuthor().getId()))
                    .type(NotificationType.NEW_REPLY)
                    .referenceId(postId)
                    .content(author.getNickname() + "님이 회원님의 댓글에 답글을 남겼습니다.")
                    .build());
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

        return CommentResponse.from(saved, currentUserId, author.getRole());
    }

    public List<CommentResponse> getComments(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId
    ) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole);

        List<TherapyPostComment> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId);
        return commentThreadAssembler.assemble(comments, currentUserId, currentUserRole);
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

        return CommentResponse.from(comment, currentUserId, currentUserRole);
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
