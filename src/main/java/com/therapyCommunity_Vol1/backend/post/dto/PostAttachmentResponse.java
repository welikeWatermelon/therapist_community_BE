package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.TherapyPostAttachment;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PostAttachmentResponse {

    private Long id;
    private String originalFilename;
    private String contentType;
    private long sizeBytes;
    private String extension;
    private String downloadUrl;
    private LocalDateTime createdAt;

    public static PostAttachmentResponse from(TherapyPostAttachment attachment) {
        return new PostAttachmentResponse(
                attachment.getId(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getExtension(),
                "/api/v1/posts/" + attachment.getPost().getId()
                        + "/attachments/" + attachment.getId() + "/download",
                attachment.getCreatedAt()
        );
    }
}
