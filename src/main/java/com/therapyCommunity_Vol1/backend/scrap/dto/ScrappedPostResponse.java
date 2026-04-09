package com.therapyCommunity_Vol1.backend.scrap.dto;

import com.therapyCommunity_Vol1.backend.post.domain.AgeGroup;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.scrap.domain.TherapyPostScrap;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ScrappedPostResponse {
    private Long postId;
    private String contentPreview;
    private String authorNickname;
    private TherapyArea therapyArea;
    private AgeGroup ageGroup;
    private Long viewCount;
    private LocalDateTime postCreatedAt;
    private LocalDateTime scrappedAt;

    public static ScrappedPostResponse from(TherapyPostScrap scrap) {
        String content = scrap.getPost().getContent();

        String plainText = content == null
                ? ""
                : content.replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();

        String preview = plainText.length() > 200
                ? plainText.substring(0,200)
                :plainText;

        return new ScrappedPostResponse(
                scrap.getPost().getId(),
                preview,
                scrap.getPost().getAuthor().getDisplayNickname(),
                scrap.getPost().getTherapyArea(),
                scrap.getPost().getAgeGroup(),
                scrap.getPost().getViewCount(),
                scrap.getPost().getCreatedAt(),
                scrap.getCreatedAt()
        );
    }
}
