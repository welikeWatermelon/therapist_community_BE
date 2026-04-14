package com.therapyCommunity_Vol1.backend.admin.dto;

import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;

import java.time.LocalDateTime;

public record AdminPostListResponse(
        Long id,
        String contentPreview,
        Long authorId,
        String authorNickname,
        PostType postType,
        TherapyArea therapyArea,
        Visibility visibility,
        Long viewCount,
        LocalDateTime createdAt,
        boolean deleted
) {
    public static AdminPostListResponse from(TherapyPost post) {
        return new AdminPostListResponse(
                post.getId(),
                makePreview(post.getContent()),
                post.getAuthor().getId(),
                post.getAuthor().getDisplayNickname(),
                post.getPostType(),
                post.getTherapyArea(),
                post.getVisibility(),
                post.getViewCount(),
                post.getCreatedAt(),
                post.isDeleted()
        );
    }

    private static String makePreview(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return "";
        }
        String plain = htmlContent
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return plain.length() > 200 ? plain.substring(0, 200) : plain;
    }
}
