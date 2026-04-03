package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.AgeGroup;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPostDownload;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DownloadedPostResponse {

    private Long postId;
    private PostType postType;
    private String title;
    private String contentPreview;
    private String authorNickname;
    private TherapyArea therapyArea;
    private AgeGroup ageGroup;
    private LocalDateTime firstDownloadedAt;
    private LocalDateTime lastDownloadedAt;
    private long downloadCount;

    public static DownloadedPostResponse from(TherapyPostDownload download) {
        String content = download.getPost().getContent();
        String plainText = content == null
                ? ""
                : content.replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();

        String preview = plainText.length() > 200
                ? plainText.substring(0, 200)
                : plainText;

        return new DownloadedPostResponse(
                download.getPost().getId(),
                download.getPost().getPostType(),
                download.getPost().getTitle(),
                preview,
                download.getPost().getAuthor().getDisplayNickname(),
                download.getPost().getTherapyArea(),
                download.getPost().getAgeGroup(),
                download.getFirstDownloadedAt(),
                download.getLastDownloadedAt(),
                download.getDownloadCount()
        );
    }
}
