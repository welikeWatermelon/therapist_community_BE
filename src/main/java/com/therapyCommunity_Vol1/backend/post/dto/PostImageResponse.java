package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.TherapyPostImage;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PostImageResponse {

    private Long id;
    private String imageUrl;
    private String originalFilename;
    private int displayOrder;
    private LocalDateTime createdAt;

    public static PostImageResponse from(TherapyPostImage image) {
        return of(image, "/api/v1/posts/" + image.getPost().getId() + "/images/" + image.getId());
    }

    public static PostImageResponse of(TherapyPostImage image, String imageUrl) {
        return new PostImageResponse(
                image.getId(),
                imageUrl,
                image.getOriginalFilename(),
                image.getDisplayOrder(),
                image.getCreatedAt()
        );
    }
}
