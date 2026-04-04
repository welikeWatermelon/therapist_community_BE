package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class TherapyPostSummaryResponse {

    private Long id;
    private PostType postType;
    private String contentPreview;
    private String authorNickname;
    private TherapyArea therapyArea;
    private Visibility visibility;
    private Long viewCount;
    private LocalDateTime createdAt;

    public TherapyPostSummaryResponse(
            Long id,
            PostType postType,
            String contentPreview,
            String authorNickname,
            TherapyArea therapyArea,
            Visibility visibility,
            Long viewCount,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.postType = postType;
        this.contentPreview = contentPreview;
        this.authorNickname = authorNickname;
        this.therapyArea = therapyArea;
        this.visibility = visibility;
        this.viewCount = viewCount;
        this.createdAt = createdAt;
    }

    public static TherapyPostSummaryResponse from(TherapyPost post) {
        return new TherapyPostSummaryResponse(
                post.getId(),
                post.getPostType(),
                makePreview(post.getContent()),
                post.getAuthor().getDisplayNickname(),
                post.getTherapyArea(),
                post.getVisibility(),
                post.getViewCount(),
                post.getCreatedAt()
        );
    }

    private static String makePreview(String htmlContent) {
        if(htmlContent == null || htmlContent.isBlank()) {
            return "";
        }

        String plainText = htmlContent
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return plainText.length() > 200
                ? plainText.substring(0,200)
                : plainText;
    }
}
