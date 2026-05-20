package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.service.upload.MediaKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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

    // VIDEO 한정. 클라가 신고한 영상 길이(초). IMAGE/ATTACHMENT 에서는 무시.
    @PositiveOrZero
    private Integer durationSec;
}
