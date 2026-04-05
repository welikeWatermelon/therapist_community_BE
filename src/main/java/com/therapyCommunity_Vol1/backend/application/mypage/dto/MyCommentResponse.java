package com.therapyCommunity_Vol1.backend.application.mypage.dto;

import java.time.LocalDateTime;

public record MyCommentResponse(
        Long commentId,
        String content,
        Long postId,
        LocalDateTime createdAt,
        boolean isDeleted
) {
}
