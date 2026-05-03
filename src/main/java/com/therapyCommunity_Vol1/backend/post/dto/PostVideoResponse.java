package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.TherapyPostVideo;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PostVideoResponse {

    private Long id;
    private String videoUrl;
    private String thumbnailUrl;
    private String originalFilename;
    private String contentType;
    private long sizeBytes;
    private Integer durationSeconds;
    private LocalDateTime createdAt;

    public static PostVideoResponse of(TherapyPostVideo video, String videoUrl, String thumbnailUrl) {
        return new PostVideoResponse(
                video.getId(),
                videoUrl,
                thumbnailUrl,
                video.getOriginalFilename(),
                video.getContentType(),
                video.getSizeBytes(),
                video.getDurationSeconds(),
                video.getCreatedAt()
        );
    }
}
