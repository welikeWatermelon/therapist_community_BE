package com.therapyCommunity_Vol1.backend.post.service.upload;

import com.therapyCommunity_Vol1.backend.file.dto.S3ObjectMeta;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.dto.UploadConfirmResponse;
import com.therapyCommunity_Vol1.backend.post.service.ActivePostFinder;
import com.therapyCommunity_Vol1.backend.post.service.PostAttachmentService;
import com.therapyCommunity_Vol1.backend.post.service.PostImageService;
import com.therapyCommunity_Vol1.backend.post.service.PostVideoService;
import com.therapyCommunity_Vol1.backend.post.service.PostVisibilityAccessPolicy;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadConfirmService {

    private final ActivePostFinder activePostFinder;
    private final PostVisibilityAccessPolicy visibilityPolicy;
    private final ResourceAccessValidator resourceAccessValidator;
    private final MediaKindPolicy mediaKindPolicy;
    private final FileStorageService fileStorageService;
    private final UploadInitService uploadInitService;
    private final MagicByteValidator magicByteValidator;

    private final PostImageService postImageService;
    private final PostAttachmentService postAttachmentService;
    private final PostVideoService postVideoService;

    @Transactional
    public UploadConfirmResponse confirm(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId,
            MediaKind kind,
            String storedKey,
            String originalFilename
    ) {
        UploadKey parsed = UploadKey.parse(storedKey);
        if (parsed.kind() != kind || !parsed.postId().equals(postId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole);
        resourceAccessValidator.validateAuthorOrAdmin(
                post.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.POST_ACCESS_DENIED);

        S3ObjectMeta meta = fileStorageService.headObject(storedKey);
        if (meta == null) {
            throw new CustomException(ErrorCode.UPLOAD_NOT_FOUND_IN_S3);
        }

        byte[] firstBytes = fileStorageService.getFirstBytes(storedKey, MagicByteValidator.READ_BYTES);
        magicByteValidator.validate(kind, firstBytes);

        String resolvedFilename = (originalFilename == null || originalFilename.isBlank())
                ? parsed.filename()
                : originalFilename;

        // S3 의 실제 size/contentType 으로 정책 재검증 (클라가 init 에서 신고한 값 위·변조 방어).
        mediaKindPolicy.validateInit(kind, resolvedFilename, meta.contentType(), meta.sizeBytes());

        if (uploadInitService.currentCount(kind, postId) >= mediaKindPolicy.perPostLimit(kind)) {
            throw new CustomException(ErrorCode.POST_MEDIA_LIMIT_EXCEEDED);
        }

        String finalKey = mediaKindPolicy.finalDirectory(kind) + "/" + UUID.randomUUID() + extOrEmpty(parsed.filename());

        fileStorageService.copy(storedKey, finalKey);

        UploadConfirmResponse response;
        try {
            response = switch (kind) {
                case IMAGE -> UploadConfirmResponse.ofImage(
                        postImageService.confirmUpload(post, finalKey, resolvedFilename, meta.contentType(), meta.sizeBytes()));
                case ATTACHMENT -> UploadConfirmResponse.ofAttachment(
                        postAttachmentService.confirmUpload(post, finalKey, resolvedFilename, meta.contentType(), meta.sizeBytes()));
                case VIDEO -> UploadConfirmResponse.ofVideo(
                        postVideoService.confirmUpload(post, finalKey, resolvedFilename, meta.contentType(), meta.sizeBytes()));
            };
        } catch (RuntimeException e) {
            safeDelete(finalKey);
            throw e;
        }

        // pending 객체는 lifecycle rule(24h) 이 정리하지만, 정상 케이스는 즉시 삭제해 비용/혼선 방지.
        // delete 실패는 best-effort.
        safeDelete(storedKey);

        return response;
    }

    private String extOrEmpty(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return "";
        }
        return filename.substring(idx);
    }

    private void safeDelete(String key) {
        try {
            fileStorageService.delete(key);
        } catch (Exception e) {
            log.warn("Failed to delete object during upload confirm cleanup: key={}", key, e);
        }
    }
}
