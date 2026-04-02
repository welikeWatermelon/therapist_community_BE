package com.therapyCommunity_Vol1.backend.user.dto;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;

import java.time.LocalDateTime;

public record MyCommentResponse(
        Long commentId,
        String content,
        Long postId,
        String postTitle,
        LocalDateTime createdAt,
        boolean isDeleted
) {
    public static MyCommentResponse from(TherapyPostComment comment) {
        return new MyCommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getPost().getId(),
                comment.getPost().getTitle(),
                comment.getCreatedAt(),
                comment.isDeleted()
        );
    }
}
