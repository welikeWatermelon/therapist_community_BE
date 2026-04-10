package com.therapyCommunity_Vol1.backend.comment.dto;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ReplyCommentResponse {

    private static final String DELETED_PLACEHOLDER = "삭제된 댓글입니다.";
    private static final String AI_USER_EMAIL = "ai-comment@system.local";

    private Long id;
    private Long postId;
    private Long parentCommentId;
    private Long authorId;
    private String authorNickname;
    private String authorRole;
    private boolean authorIsAi;
    private String content;
    private boolean deleted;
    private boolean canEdit;
    private boolean canDelete;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReplyCommentResponse from(
            TherapyPostComment comment,
            Long currentUserId,
            UserRole currentUserRole
    ) {
        boolean canManage = !comment.isDeleted() && canManage(comment, currentUserId, currentUserRole);
        return new ReplyCommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getParentComment() != null ? comment.getParentComment().getId() : null,
                comment.getAuthor().getId(),
                comment.getAuthor().getDisplayNickname(),
                comment.getAuthor().getRole().getCode(),
                AI_USER_EMAIL.equals(comment.getAuthor().getEmail()),
                comment.isDeleted() ? DELETED_PLACEHOLDER : comment.getContent(),
                comment.isDeleted(),
                canManage,
                canManage,
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }

    private static boolean canManage(
            TherapyPostComment comment,
            Long currentUserId,
            UserRole currentUserRole
    ) {
        boolean isAdmin = currentUserRole == UserRole.ADMIN;
        boolean isAuthor = comment.getAuthor().getId().equals(currentUserId);
        return isAdmin || isAuthor;
    }
}
