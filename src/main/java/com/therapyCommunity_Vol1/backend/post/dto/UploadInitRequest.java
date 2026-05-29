package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.service.upload.MediaKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UploadInitRequest {

    @NotNull
    private MediaKind kind;

    @NotBlank
    private String originalFilename;

    @NotBlank
    private String contentType;

    @Positive
    private long sizeBytes;
}
