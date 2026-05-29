package com.therapyCommunity_Vol1.backend.post.service.upload;

import com.therapyCommunity_Vol1.backend.file.dto.S3ObjectMeta;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.dto.PostImageResponse;
import com.therapyCommunity_Vol1.backend.post.dto.UploadConfirmResponse;
import com.therapyCommunity_Vol1.backend.post.service.ActivePostFinder;
import com.therapyCommunity_Vol1.backend.post.service.PostAttachmentService;
import com.therapyCommunity_Vol1.backend.post.service.PostImageService;
import com.therapyCommunity_Vol1.backend.post.service.PostVideoService;
import com.therapyCommunity_Vol1.backend.post.service.PostVisibilityAccessPolicy;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
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
        log.info("upload confirm received: userId={}, postId={}, kind={}, storedKey={}",
                currentUserId, postId, kind, storedKey);

        UploadKey parsed = UploadKey.parse(storedKey);
        if (parsed.kind() != kind || !parsed.postId().equals(postId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        TherapyPost post = activePostFinder.findOrThrow(postId);
        if (post.getPostType() == PostType.CONCERN_CARD) {
            throw new CustomException(ErrorCode.CONCERN_CARD_UPLOAD_NOT_ALLOWED);
        }
        visibilityPolicy.checkAccess(post, currentUserRole, currentUserId);
        resourceAccessValidator.validateAuthorOrAdmin(
                post.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.POST_ACCESS_DENIED);

        String finalKey = finalKeyFor(kind, parsed.filename());

        // IMAGE 멱등: 이미 confirm 되어 finalKey 가 영속됐으면 S3·persist 스킵하고 기존 결과 반환.
        // (성공 후 pending 이 삭제된 상태의 재시도를 에러 없이 처리.)
        if (kind == MediaKind.IMAGE) {
            Optional<PostImageResponse> existing = postImageService.findByStoredPath(finalKey);
            if (existing.isPresent()) {
                safeDelete(storedKey);
                log.info("upload confirm idempotent hit: userId={}, postId={}, kind={}, storedKey={}, finalKey={}",
                        currentUserId, postId, kind, storedKey, finalKey);
                return UploadConfirmResponse.ofImage(existing.get());
            }
        }

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
        } catch (DataIntegrityViolationException e) {
            // 거의 불가능한 동시 race: 다른 요청이 같은 finalKey 로 먼저 저장해 stored_path 유니크 위반.
            // finalKey 는 그 레코드(승자)가 참조하므로 절대 삭제하지 않고 그대로 실패시킨다(중복 0 보장).
            // PG는 위반 후 트랜잭션을 abort 하므로 같은 tx 안에서 재조회로 흡수하지 않는다.
            // 이 클라이언트가 재시도하면 진입부 findByStoredPath 단락으로 멱등 복구된다.
            log.warn("upload confirm unique conflict (concurrent retry?): postId={}, kind={}, storedKey={}, finalKey={}",
                    postId, kind, storedKey, finalKey);
            throw new CustomException(ErrorCode.UPLOAD_CONFIRM_CONFLICT);
        } catch (RuntimeException e) {
            safeDelete(finalKey);
            throw e;
        }

        // pending 객체는 lifecycle rule(24h) 이 정리하지만, 정상 케이스는 즉시 삭제해 비용/혼선 방지.
        // delete 실패는 best-effort.
        safeDelete(storedKey);

        log.info("upload confirm success: userId={}, postId={}, kind={}, storedKey={}, finalKey={}",
                currentUserId, postId, kind, storedKey, finalKey);

        return response;
    }

    private void safeDelete(String key) {
        try {
            fileStorageService.delete(key);
        } catch (Exception e) {
            log.warn("Failed to delete object during upload confirm cleanup: key={}", key, e);
        }
    }

    private String finalKeyFor(MediaKind kind, String filename) {
        String directory = mediaKindPolicy.finalDirectory(kind);
        if (kind == MediaKind.IMAGE) {
            return directory + "/" + filename;
        }
        return directory + "/" + UUID.randomUUID() + extOrEmpty(filename);
    }

    private String extOrEmpty(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return "";
        }
        return filename.substring(idx);
    }
}
