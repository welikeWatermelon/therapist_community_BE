package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.AgeGroup;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class TherapyPostResponse {

    private Long id;
    private PostType postType;
    private String title;
    private String contentPreview;
    private String authorNickname;
    private TherapyArea therapyArea;
    private AgeGroup ageGroup;
    private Long viewCount;
    private LocalDateTime createdAt;

    public TherapyPostResponse(TherapyPost post) {
        this.id = post.getId();
        this.postType = post.getPostType();
        this.title = post.getTitle();
        this.authorNickname = post.getAuthor().getNickname();
        this.therapyArea = post.getTherapyArea();
        this.ageGroup = post.getAgeGroup();
        this.viewCount = post.getViewCount();
        this.createdAt = post.getCreatedAt();

        String content = post.getContent();
        this.contentPreview =
                content.length() > 200
                    ? content.substring(0,200)
                    : content;
    }
}
