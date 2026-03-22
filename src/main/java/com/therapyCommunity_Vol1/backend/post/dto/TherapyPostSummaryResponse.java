package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.AgeGroup;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class TherapyPostSummaryResponse {

    private Long id;
    private PostType postType;
    private String title;
    private String contentPreview;
    private String authorNickname;
    private TherapyArea therapyArea;
    private AgeGroup ageGroup;
    private Long viewCount;
    private LocalDateTime createdAt;

    public TherapyPostSummaryResponse(
            Long id,
            PostType postType,
            String title,
            String contentPreview,
            String authorNickname,
            TherapyArea therapyArea,
            AgeGroup ageGroup,
            Long viewCount,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.postType = postType;
        this.title = title;
        this.contentPreview = contentPreview;
        this.authorNickname = authorNickname;
        this.therapyArea = therapyArea;
        this.ageGroup = ageGroup;
        this.viewCount = viewCount;
        this.createdAt = createdAt;
    }

    public static TherapyPostSummaryResponse from(TherapyPost post) {
        return new TherapyPostSummaryResponse(
                post.getId(),
                post.getPostType(),
                post.getTitle(),
                makePreview(post.getContent()),
                post.getAuthor().getNickname(),
                post.getTherapyArea(),
                post.getAgeGroup(),
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
