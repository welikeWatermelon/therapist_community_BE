package com.therapyCommunity_Vol1.backend.admin.dto;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;

import java.time.LocalDateTime;

public record AdminCommentListResponse(
        Long id,
        String content,
        Long authorId,
        String authorNickname,
        Long postId,
        Long parentCommentId,
        LocalDateTime createdAt,
        boolean deleted
) {
    public static AdminCommentListResponse from(TherapyPostComment comment) {
        return new AdminCommentListResponse(
                comment.getId(),
                comment.isDeleted() ? "삭제된 댓글입니다." : comment.getContent(),
                comment.getAuthor().getId(),
                comment.getAuthor().getDisplayNickname(),
                comment.getPost().getId(),
                comment.getParentComment() != null ? comment.getParentComment().getId() : null,
                comment.getCreatedAt(),
                comment.isDeleted()
        );
    }
}
