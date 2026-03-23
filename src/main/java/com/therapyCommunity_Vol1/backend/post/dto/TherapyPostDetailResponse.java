package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.AgeGroup;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class TherapyPostDetailResponse {

    private Long id;
    private String title;
    private String content;
    private PostType postType;
    private String authorNickname;
    private TherapyArea therapyArea;
    private AgeGroup ageGroup;
    private Long viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PostAttachmentResponse> attachments;

    public static TherapyPostDetailResponse from(TherapyPost post) {
        return from(post, List.of());
    }

    public static TherapyPostDetailResponse from(
            TherapyPost post,
            List<PostAttachmentResponse> attachments
    ) {
        return new TherapyPostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getPostType(),
                post.getAuthor().getNickname(),
                post.getTherapyArea(),
                post.getAgeGroup(),
                post.getViewCount(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                attachments
        );
    }
}
