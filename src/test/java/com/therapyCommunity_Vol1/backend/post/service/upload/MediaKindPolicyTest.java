package com.therapyCommunity_Vol1.backend.post.service.upload;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaKindPolicyTest {

    private final MediaKindPolicy policy = new MediaKindPolicy();
    private static final long MB = 1024L * 1024L;

    @Test
    void validateInit_image_passes_for_jpg_under_10mb() {
        policy.validateInit(MediaKind.IMAGE, "photo.jpg", "image/jpeg", 5 * MB);
    }

    @Test
    void validateInit_image_rejects_unsupported_extension() {
        assertThatThrownBy(() -> policy.validateInit(MediaKind.IMAGE, "photo.gif", "image/gif", 1 * MB))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_POST_IMAGE);
    }

    @Test
    void validateInit_image_rejects_size_over_10mb() {
        assertThatThrownBy(() -> policy.validateInit(MediaKind.IMAGE, "photo.jpg", "image/jpeg", 11 * MB))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_POST_IMAGE);
    }

    @Test
    void validateInit_image_rejects_mismatched_contentType() {
        assertThatThrownBy(() -> policy.validateInit(MediaKind.IMAGE, "photo.jpg", "image/svg+xml", 1 * MB))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void validateInit_attachment_accepts_pdf_docx_xlsx() {
        policy.validateInit(MediaKind.ATTACHMENT, "doc.pdf", "application/pdf", 1 * MB);
        policy.validateInit(MediaKind.ATTACHMENT, "doc.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 1 * MB);
        policy.validateInit(MediaKind.ATTACHMENT, "sheet.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 1 * MB);
    }

    @Test
    void validateInit_attachment_accepts_hwp_via_extension_only_when_contentType_is_octet_stream() {
        policy.validateInit(MediaKind.ATTACHMENT, "doc.hwp", "application/octet-stream", 1 * MB);
        // hwp는 contentType이 다른 값이어도 통과 (확장자 화이트리스트만 검증)
        policy.validateInit(MediaKind.ATTACHMENT, "doc.hwp", "application/x-hwp", 1 * MB);
    }

    @Test
    void validateInit_attachment_rejects_exe() {
        assertThatThrownBy(() -> policy.validateInit(MediaKind.ATTACHMENT, "malware.exe", "application/x-msdownload", 1 * MB))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_POST_ATTACHMENT);
    }

    @Test
    void validateInit_attachment_rejects_size_over_50mb() {
        assertThatThrownBy(() -> policy.validateInit(MediaKind.ATTACHMENT, "doc.pdf", "application/pdf", 51 * MB))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_POST_ATTACHMENT);
    }

    @Test
    void validateInit_video_accepts_mp4_mov() {
        policy.validateInit(MediaKind.VIDEO, "v.mp4", "video/mp4", 100 * MB);
        policy.validateInit(MediaKind.VIDEO, "v.mov", "video/quicktime", 100 * MB);
    }

    @Test
    void validateInit_video_rejects_webm() {
        assertThatThrownBy(() -> policy.validateInit(MediaKind.VIDEO, "v.webm", "video/webm", 100 * MB))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_POST_VIDEO);
    }

    @Test
    void validateInit_video_rejects_avi() {
        assertThatThrownBy(() -> policy.validateInit(MediaKind.VIDEO, "v.avi", "video/x-msvideo", 100 * MB))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_POST_VIDEO);
    }

    @Test
    void validateInit_video_accepts_size_up_to_512mb() {
        policy.validateInit(MediaKind.VIDEO, "v.mp4", "video/mp4", 512 * MB);
    }

    @Test
    void validateInit_video_rejects_size_over_512mb() {
        assertThatThrownBy(() -> policy.validateInit(MediaKind.VIDEO, "v.mp4", "video/mp4", 513 * MB))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_POST_VIDEO);
    }

    @Test
    void validateVideoDuration_accepts_up_to_300s() {
        policy.validateVideoDuration(1);
        policy.validateVideoDuration(300);
    }

    @Test
    void validateVideoDuration_rejects_over_300s() {
        assertThatThrownBy(() -> policy.validateVideoDuration(301))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_VIDEO_DURATION_EXCEEDED);
    }

    @Test
    void validateVideoDuration_rejects_null_or_zero() {
        assertThatThrownBy(() -> policy.validateVideoDuration(null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_VIDEO_DURATION_INVALID);
        assertThatThrownBy(() -> policy.validateVideoDuration(0))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_VIDEO_DURATION_INVALID);
    }

    @Test
    void validateInit_rejectsZeroOrNegativeSize() {
        assertThatThrownBy(() -> policy.validateInit(MediaKind.IMAGE, "p.jpg", "image/jpeg", 0))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void validateInit_rejectsNullKind() {
        assertThatThrownBy(() -> policy.validateInit(null, "p.jpg", "image/jpeg", 1 * MB))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_UPLOAD_KIND);
    }

    @ParameterizedTest
    @CsvSource({
            "IMAGE,uploads-pending/images,post-images,10",
            "ATTACHMENT,uploads-pending/attachments,post-attachments,5",
            "VIDEO,uploads-pending/videos,post-videos,1"
    })
    void directoriesAndLimits(String kind, String pendingDir, String finalDir, int perPostLimit) {
        MediaKind k = MediaKind.valueOf(kind);
        assertThat(policy.pendingDirectory(k)).isEqualTo(pendingDir);
        assertThat(policy.finalDirectory(k)).isEqualTo(finalDir);
        assertThat(policy.perPostLimit(k)).isEqualTo(perPostLimit);
    }

    @ParameterizedTest
    @CsvSource({
            "IMAGE,10",
            "ATTACHMENT,50",
            "VIDEO,512"
    })
    void perFileLimitMatchesPolicy(String kind, long mb) {
        assertThat(policy.perFileLimitBytes(MediaKind.valueOf(kind))).isEqualTo(mb * MB);
    }
}
