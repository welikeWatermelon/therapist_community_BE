package com.therapyCommunity_Vol1.backend.post.service.upload;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadKeyTest {

    @Test
    void parse_validKey_returnsKindAndPostId() {
        UploadKey key = UploadKey.parse("uploads-pending/images/42/abc-123.jpg");
        assertThat(key.kind()).isEqualTo(MediaKind.IMAGE);
        assertThat(key.postId()).isEqualTo(42L);
        assertThat(key.filename()).isEqualTo("abc-123.jpg");
    }

    @Test
    void parse_attachmentDirMapsToAttachment() {
        UploadKey key = UploadKey.parse("uploads-pending/attachments/7/file.pdf");
        assertThat(key.kind()).isEqualTo(MediaKind.ATTACHMENT);
    }

    @Test
    void parse_videoDirMapsToVideo() {
        UploadKey key = UploadKey.parse("uploads-pending/videos/1/v.mp4");
        assertThat(key.kind()).isEqualTo(MediaKind.VIDEO);
    }

    @Test
    void parse_wrongPrefix_throws() {
        assertThatThrownBy(() -> UploadKey.parse("post-images/42/abc.jpg"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void parse_unknownKindDir_throws() {
        assertThatThrownBy(() -> UploadKey.parse("uploads-pending/unknown/42/abc.jpg"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void parse_pathTraversal_throws() {
        assertThatThrownBy(() -> UploadKey.parse("uploads-pending/images/42/..%2Fetc%2Fpasswd"))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> UploadKey.parse("uploads-pending/../../etc/passwd"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void parse_nullThrows() {
        assertThatThrownBy(() -> UploadKey.parse(null))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void parse_negativePostId_throws() {
        assertThatThrownBy(() -> UploadKey.parse("uploads-pending/images/-1/abc.jpg"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void format_roundTripsThroughParse() {
        UploadKey original = UploadKey.generate(MediaKind.IMAGE, 99L, "jpg");
        String formatted = original.format();
        UploadKey roundTripped = UploadKey.parse(formatted);
        assertThat(roundTripped.kind()).isEqualTo(original.kind());
        assertThat(roundTripped.postId()).isEqualTo(original.postId());
        assertThat(roundTripped.filename()).isEqualTo(original.filename());
    }

    @Test
    void generate_stripsNonAlphanumericFromExtension() {
        UploadKey k = UploadKey.generate(MediaKind.IMAGE, 1L, "jpg/../etc");
        assertThat(k.filename()).matches("[A-Fa-f0-9-]+\\.jpgetc");
    }

    @Test
    void generate_handlesEmptyExtension() {
        UploadKey k = UploadKey.generate(MediaKind.VIDEO, 1L, "");
        assertThat(k.filename()).doesNotContain(".");
    }
}
