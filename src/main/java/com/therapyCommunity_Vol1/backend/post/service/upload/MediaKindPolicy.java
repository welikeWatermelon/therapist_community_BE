package com.therapyCommunity_Vol1.backend.post.service.upload;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class MediaKindPolicy {

    private static final long MB = 1024L * 1024L;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private static final Set<String> ATTACHMENT_EXTENSIONS = Set.of("pdf", "docx", "xlsx", "hwp");
    private static final Set<String> ATTACHMENT_MIME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/octet-stream"
    );

    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov");
    private static final Set<String> VIDEO_MIME_TYPES = Set.of("video/mp4", "video/quicktime");

    private static final long IMAGE_MAX_BYTES = 10 * MB;
    private static final long ATTACHMENT_MAX_BYTES = 50 * MB;
    private static final long VIDEO_MAX_BYTES = 512 * MB;
    private static final int VIDEO_MAX_DURATION_SEC = 300;

    private static final int IMAGE_PER_POST_LIMIT = 10;
    private static final int ATTACHMENT_PER_POST_LIMIT = 5;
    private static final int VIDEO_PER_POST_LIMIT = 1;

    public void validateInit(MediaKind kind, String originalFilename, String contentType, long sizeBytes) {
        if (kind == null) {
            throw new CustomException(ErrorCode.INVALID_UPLOAD_KIND);
        }
        if (sizeBytes <= 0 || sizeBytes > perFileLimitBytes(kind)) {
            throw kindError(kind);
        }

        String ext = extractExtension(originalFilename);
        String mime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();

        switch (kind) {
            case IMAGE -> {
                if (!IMAGE_EXTENSIONS.contains(ext) || !IMAGE_MIME_TYPES.contains(mime)) {
                    throw kindError(kind);
                }
            }
            case ATTACHMENT -> {
                if (!ATTACHMENT_EXTENSIONS.contains(ext)) {
                    throw kindError(kind);
                }
                // hwp는 표준 MIME이 없어 octet-stream으로 들어올 수 있음 → 확장자 화이트리스트만으로 통과
                if (!"hwp".equals(ext) && !ATTACHMENT_MIME_TYPES.contains(mime)) {
                    throw kindError(kind);
                }
            }
            case VIDEO -> {
                if (!VIDEO_EXTENSIONS.contains(ext) || !VIDEO_MIME_TYPES.contains(mime)) {
                    throw kindError(kind);
                }
            }
        }
    }

    public String pendingDirectory(MediaKind kind) {
        return switch (kind) {
            case IMAGE -> "uploads-pending/images";
            case ATTACHMENT -> "uploads-pending/attachments";
            case VIDEO -> "uploads-pending/videos";
        };
    }

    public String finalDirectory(MediaKind kind) {
        return switch (kind) {
            case IMAGE -> "post-images";
            case ATTACHMENT -> "post-attachments";
            case VIDEO -> "post-videos";
        };
    }

    public int perPostLimit(MediaKind kind) {
        return switch (kind) {
            case IMAGE -> IMAGE_PER_POST_LIMIT;
            case ATTACHMENT -> ATTACHMENT_PER_POST_LIMIT;
            case VIDEO -> VIDEO_PER_POST_LIMIT;
        };
    }

    public long perFileLimitBytes(MediaKind kind) {
        return switch (kind) {
            case IMAGE -> IMAGE_MAX_BYTES;
            case ATTACHMENT -> ATTACHMENT_MAX_BYTES;
            case VIDEO -> VIDEO_MAX_BYTES;
        };
    }

    public void validateVideoDuration(Integer durationSec) {
        if (durationSec == null || durationSec <= 0) {
            throw new CustomException(ErrorCode.UPLOAD_VIDEO_DURATION_INVALID);
        }
        if (durationSec > VIDEO_MAX_DURATION_SEC) {
            throw new CustomException(ErrorCode.UPLOAD_VIDEO_DURATION_EXCEEDED);
        }
    }

    public String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int idx = originalFilename.lastIndexOf('.');
        if (idx < 0 || idx == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private CustomException kindError(MediaKind kind) {
        return switch (kind) {
            case IMAGE -> new CustomException(ErrorCode.INVALID_POST_IMAGE);
            case ATTACHMENT -> new CustomException(ErrorCode.INVALID_POST_ATTACHMENT);
            case VIDEO -> new CustomException(ErrorCode.INVALID_POST_VIDEO);
        };
    }
}
