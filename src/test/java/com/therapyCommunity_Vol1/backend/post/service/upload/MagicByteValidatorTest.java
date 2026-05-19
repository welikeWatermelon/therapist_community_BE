package com.therapyCommunity_Vol1.backend.post.service.upload;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MagicByteValidatorTest {

    private final MagicByteValidator validator = new MagicByteValidator();

    // JPEG: FF D8 FF
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    // PNG: 89 50 4E 47 0D 0A 1A 0A
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    // WebP: RIFF????WEBP
    private static final byte[] WEBP = {0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00,
            0x57, 0x45, 0x42, 0x50, 0x00, 0x00, 0x00, 0x00};

    // PDF: %PDF
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    // ZIP (DOCX/XLSX): PK
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    // OLE Compound (HWP): D0 CF 11 E0
    private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    // HWP5: "HWP "
    private static final byte[] HWP5 = {0x48, 0x57, 0x50, 0x20, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    // MP4: ftyp at offset 4
    private static final byte[] MP4 = {0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70,
            0x69, 0x73, 0x6F, 0x6D, 0x00, 0x00, 0x00, 0x00};

    // WebM: EBML 1A 45 DF A3
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    @Test
    void validate_jpeg_passes() {
        assertThatCode(() -> validator.validate(MediaKind.IMAGE, JPEG)).doesNotThrowAnyException();
    }

    @Test
    void validate_png_passes() {
        assertThatCode(() -> validator.validate(MediaKind.IMAGE, PNG)).doesNotThrowAnyException();
    }

    @Test
    void validate_webp_passes() {
        assertThatCode(() -> validator.validate(MediaKind.IMAGE, WEBP)).doesNotThrowAnyException();
    }

    @Test
    void validate_pdf_passes() {
        assertThatCode(() -> validator.validate(MediaKind.ATTACHMENT, PDF)).doesNotThrowAnyException();
    }

    @Test
    void validate_docx_zip_passes() {
        assertThatCode(() -> validator.validate(MediaKind.ATTACHMENT, ZIP)).doesNotThrowAnyException();
    }

    @Test
    void validate_hwp_ole_passes() {
        assertThatCode(() -> validator.validate(MediaKind.ATTACHMENT, OLE)).doesNotThrowAnyException();
    }

    @Test
    void validate_hwp5_passes() {
        assertThatCode(() -> validator.validate(MediaKind.ATTACHMENT, HWP5)).doesNotThrowAnyException();
    }

    @Test
    void validate_mp4_passes() {
        assertThatCode(() -> validator.validate(MediaKind.VIDEO, MP4)).doesNotThrowAnyException();
    }

    @Test
    void validate_webm_passes() {
        assertThatCode(() -> validator.validate(MediaKind.VIDEO, WEBM)).doesNotThrowAnyException();
    }

    @Test
    void validate_pdfAsImage_throws() {
        assertThatThrownBy(() -> validator.validate(MediaKind.IMAGE, PDF))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_MIME_MISMATCH);
    }

    @Test
    void validate_jpegAsVideo_throws() {
        assertThatThrownBy(() -> validator.validate(MediaKind.VIDEO, JPEG))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_MIME_MISMATCH);
    }

    @Test
    void validate_emptyBytes_throws() {
        assertThatThrownBy(() -> validator.validate(MediaKind.IMAGE, new byte[0]))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_MIME_MISMATCH);
    }

    @Test
    void validate_nullBytes_throws() {
        assertThatThrownBy(() -> validator.validate(MediaKind.IMAGE, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_MIME_MISMATCH);
    }

    @Test
    void validate_shortBytes_throws() {
        assertThatThrownBy(() -> validator.validate(MediaKind.IMAGE, new byte[]{(byte) 0xFF, (byte) 0xD8}))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_MIME_MISMATCH);
    }

    @Test
    void validate_mp4AsAttachment_throws() {
        assertThatThrownBy(() -> validator.validate(MediaKind.ATTACHMENT, MP4))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UPLOAD_MIME_MISMATCH);
    }
}
