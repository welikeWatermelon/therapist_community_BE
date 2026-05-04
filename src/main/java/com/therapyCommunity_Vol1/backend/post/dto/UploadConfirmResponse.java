package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.service.upload.MediaKind;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 단일 confirm 엔드포인트가 kind 별 도메인 응답을 반환하기 위한 wrapper.
 * 정확히 한 필드만 채워지고 나머지는 null.
 */
@Getter
@AllArgsConstructor
public class UploadConfirmResponse {

    private MediaKind kind;
    private PostImageResponse image;
    private PostAttachmentResponse attachment;
    private PostVideoResponse video;

    public static UploadConfirmResponse ofImage(PostImageResponse image) {
        return new UploadConfirmResponse(MediaKind.IMAGE, image, null, null);
    }

    public static UploadConfirmResponse ofAttachment(PostAttachmentResponse attachment) {
        return new UploadConfirmResponse(MediaKind.ATTACHMENT, null, attachment, null);
    }

    public static UploadConfirmResponse ofVideo(PostVideoResponse video) {
        return new UploadConfirmResponse(MediaKind.VIDEO, null, null, video);
    }
}
