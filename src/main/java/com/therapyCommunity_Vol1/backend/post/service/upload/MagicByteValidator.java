package com.therapyCommunity_Vol1.backend.post.service.upload;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * S3에서 읽은 실제 첫 바이트로 파일 타입을 검증.
 * 클라이언트가 Content-Type 헤더를 위조해도 실제 바이트 기반으로 차단.
 */
@Component
public class MagicByteValidator {

    static final int READ_BYTES = 16;

    public void validate(MediaKind kind, byte[] firstBytes) {
        if (firstBytes == null || firstBytes.length < 4) {
            throw new CustomException(ErrorCode.UPLOAD_MIME_MISMATCH);
        }
        boolean valid = switch (kind) {
            case IMAGE -> isJpeg(firstBytes) || isPng(firstBytes) || isWebP(firstBytes);
            case ATTACHMENT -> isPdf(firstBytes) || isZipBased(firstBytes) || isOleCompound(firstBytes) || isHwp5(firstBytes);
            case VIDEO -> isMp4orMov(firstBytes);
        };
        if (!valid) {
            throw new CustomException(ErrorCode.UPLOAD_MIME_MISMATCH);
        }
    }

    // FF D8 FF
    private boolean isJpeg(byte[] b) {
        return b[0] == (byte) 0xFF && b[1] == (byte) 0xD8 && b[2] == (byte) 0xFF;
    }

    // 89 50 4E 47 0D 0A 1A 0A
    private boolean isPng(byte[] b) {
        return b.length >= 8
                && b[0] == (byte) 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
    }

    // RIFF????WEBP
    private boolean isWebP(byte[] b) {
        return b.length >= 12
                && b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46
                && b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 && b[11] == 0x50;
    }

    // %PDF
    private boolean isPdf(byte[] b) {
        return b[0] == 0x25 && b[1] == 0x50 && b[2] == 0x44 && b[3] == 0x46;
    }

    // PK (ZIP-based: DOCX, XLSX)
    private boolean isZipBased(byte[] b) {
        return b[0] == 0x50 && b[1] == 0x4B;
    }

    // D0 CF 11 E0 (OLE Compound — HWP 97~2010)
    private boolean isOleCompound(byte[] b) {
        return b[0] == (byte) 0xD0 && b[1] == (byte) 0xCF && b[2] == 0x11 && b[3] == (byte) 0xE0;
    }

    // "HWP " (HWP 2010+, V5.0 format)
    private boolean isHwp5(byte[] b) {
        return b[0] == 0x48 && b[1] == 0x57 && b[2] == 0x50 && b[3] == 0x20;
    }

    // ftyp box at offset 4: MP4 / MOV
    private boolean isMp4orMov(byte[] b) {
        return b.length >= 8 && b[4] == 0x66 && b[5] == 0x74 && b[6] == 0x79 && b[7] == 0x70;
    }
}
