package com.therapyCommunity_Vol1.backend.post.service.upload;

import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.dto.UploadInitResponse;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostAttachmentRepository;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostImageRepository;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostVideoRepository;
import com.therapyCommunity_Vol1.backend.post.service.ActivePostFinder;
import com.therapyCommunity_Vol1.backend.post.service.PostVisibilityAccessPolicy;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UploadInitService {

    static final Duration UPLOAD_PRESIGN_TTL = Duration.ofMinutes(5);

    private final ActivePostFinder activePostFinder;
    private final PostVisibilityAccessPolicy visibilityPolicy;
    private final ResourceAccessValidator resourceAccessValidator;
    private final MediaKindPolicy mediaKindPolicy;
    private final FileStorageService fileStorageService;
    private final UploadRateLimiter uploadRateLimiter;

    private final TherapyPostImageRepository imageRepository;
    private final TherapyPostAttachmentRepository attachmentRepository;
    private final TherapyPostVideoRepository videoRepository;

    public UploadInitResponse init(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId,
            MediaKind kind,
            String originalFilename,
            String contentType,
            long sizeBytes
    ) {
        uploadRateLimiter.checkAndIncrement(currentUserId);

        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole, currentUserId);
        resourceAccessValidator.validateAuthorOrAdmin(
                post.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.POST_ACCESS_DENIED);

        mediaKindPolicy.validateInit(kind, originalFilename, contentType, sizeBytes);

        if (currentCount(kind, postId) >= mediaKindPolicy.perPostLimit(kind)) {
            throw new CustomException(ErrorCode.POST_MEDIA_LIMIT_EXCEEDED);
        }

        UploadKey key = UploadKey.generate(kind, postId, mediaKindPolicy.extractExtension(originalFilename));
        String storedKey = key.format();

        String uploadUrl = fileStorageService.presignPut(storedKey, contentType, UPLOAD_PRESIGN_TTL);
        if (uploadUrl == null) {
            // LocalFileStorageService 등 presigned PUT 미지원 환경 → 503.
            // dev 는 deprecated multipart 엔드포인트 사용.
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR);
        }

        // confirm 로그와 storedKey 로 짝지어 "init 발급됐으나 confirm 미도달"(브라우저 PUT 실패 등) 을 추적.
        log.info("upload init issued: userId={}, postId={}, kind={}, storedKey={}, contentType={}, sizeBytes={}, filename={}",
                currentUserId, postId, kind, storedKey, contentType, sizeBytes, originalFilename);

        return new UploadInitResponse(
                uploadUrl,
                storedKey,
                Instant.now().plus(UPLOAD_PRESIGN_TTL)
        );
    }

    int currentCount(MediaKind kind, Long postId) {
        return switch (kind) {
            case IMAGE -> imageRepository.countByPostId(postId);
            case ATTACHMENT -> attachmentRepository.countByPostId(postId);
            case VIDEO -> videoRepository.countByPostId(postId);
        };
    }
}
