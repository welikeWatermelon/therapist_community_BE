package com.therapyCommunity_Vol1.backend.user.dto;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;

import java.time.LocalDateTime;

public record MyCommentResponse(
        Long commentId,
        String content,
        Long postId,
        LocalDateTime createdAt,
        boolean isDeleted
) {
    private static final String DELETED_CONTENT = "삭제된 댓글입니다";

    public static MyCommentResponse from(TherapyPostComment comment) {
        String content = comment.isDeleted() ? DELETED_CONTENT : comment.getContent();
        return new MyCommentResponse(
                comment.getId(),
                content,
                comment.getPost().getId(),
                comment.getCreatedAt(),
                comment.isDeleted()
        );
    }
}
