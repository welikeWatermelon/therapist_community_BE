package com.therapyCommunity_Vol1.backend.comment.service;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.comment.dto.CommentResponse;
import com.therapyCommunity_Vol1.backend.comment.dto.CreateCommentRequest;
import com.therapyCommunity_Vol1.backend.comment.dto.UpdateCommentRequest;
import com.therapyCommunity_Vol1.backend.comment.repository.TherapyPostCommentRepository;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final TherapyPostCommentRepository commentRepository;
    private final TherapyPostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public CommentResponse createComment(
            Long currentUserId,
            Long postId,
            CreateCommentRequest request
    ) {
        User author = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        TherapyPost post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

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

        return CommentResponse.from(saved);
    }

    public List<CommentResponse> getComments(Long postId) {
        postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId)
                .stream()
                .map(CommentResponse::from)
                .toList();
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

        validateAuthorOrAdmin(comment, currentUserId, currentUserRole);

        comment.update(request.getContent());

        return CommentResponse.from(comment);
    }

    @Transactional
    public void deleteComment(
            Long currentUserId,
            UserRole currentUserRole,
            Long commentId
    ) {
        TherapyPostComment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
        validateAuthorOrAdmin(comment, currentUserId, currentUserRole);

        comment.softDelete();
    }

    private void validateAuthorOrAdmin(
            TherapyPostComment comment,
            Long currentUserId,
            UserRole currentUserRole
    ) {
        boolean isAdmin = currentUserRole == UserRole.ADMIN;
        boolean isAuthor = comment.getAuthor().getId().equals(currentUserId);

        if (!isAdmin && !isAuthor) {
            throw new CustomException(ErrorCode.COMMENT_ACCESS_DENIED);
        }
    }
}
