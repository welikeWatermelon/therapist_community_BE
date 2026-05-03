package com.therapyCommunity_Vol1.backend.post.service.upload;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pending storedKey 의 형식: uploads-pending/{kind-dir}/{postId}/{uuid}[.{ext}]
 * 백엔드만 키를 생성하므로 클라가 임의 경로를 confirm 으로 보내는 시도를 패턴 검증으로 차단.
 */
public record UploadKey(MediaKind kind, Long postId, String filename) {

    private static final Pattern PATTERN = Pattern.compile(
            "^uploads-pending/(images|attachments|videos)/(\\d+)/([A-Za-z0-9._-]+)$"
    );

    public static UploadKey generate(MediaKind kind, Long postId, String extension) {
        String safeExt = extension == null ? "" : extension.replaceAll("[^A-Za-z0-9]", "");
        String filename = UUID.randomUUID() + (safeExt.isEmpty() ? "" : "." + safeExt);
        return new UploadKey(kind, postId, filename);
    }

    public static UploadKey parse(String storedKey) {
        if (storedKey == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        Matcher m = PATTERN.matcher(storedKey);
        if (!m.matches()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        MediaKind kind = switch (m.group(1)) {
            case "images" -> MediaKind.IMAGE;
            case "attachments" -> MediaKind.ATTACHMENT;
            case "videos" -> MediaKind.VIDEO;
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
        Long postId = Long.parseLong(m.group(2));
        String filename = m.group(3);
        if (filename.contains("..")) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return new UploadKey(kind, postId, filename);
    }

    public String format() {
        String kindDir = switch (kind) {
            case IMAGE -> "images";
            case ATTACHMENT -> "attachments";
            case VIDEO -> "videos";
        };
        return "uploads-pending/" + kindDir + "/" + postId + "/" + filename;
    }
}
