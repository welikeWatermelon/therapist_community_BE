package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.analytics.domain.EventTargetType;
import com.therapyCommunity_Vol1.backend.analytics.domain.UserEventType;
import com.therapyCommunity_Vol1.backend.analytics.event.UserEventPublisher;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPostAttachment;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPostDownload;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.post.dto.DownloadedPostResponse;
import com.therapyCommunity_Vol1.backend.post.dto.PostAttachmentResponse;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostAttachmentRepository;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostDownloadRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostAttachmentService {

    private static final String EVT_ATTACHMENT_DELETE_FAILED = "POST_ATTACHMENT_DELETE_FAILED";
    private static final Duration ATTACHMENT_PRESIGN_TTL = Duration.ofHours(1);

    private final ActivePostFinder activePostFinder;
    private final TherapyPostAttachmentRepository therapyPostAttachmentRepository;
    private final TherapyPostDownloadRepository therapyPostDownloadRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final ResourceAccessValidator resourceAccessValidator;
    private final PostVisibilityAccessPolicy visibilityPolicy;
    private final UserEventPublisher userEventPublisher;

    @Transactional
    public PostAttachmentResponse uploadAttachment(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId,
            MultipartFile file
    ) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole, currentUserId);
        resourceAccessValidator.validateAuthorOrAdmin(post.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.POST_ACCESS_DENIED);

        StoredFileInfo storedFileInfo = fileStorageService.storePostAttachment(file);

        try {
            TherapyPostAttachment attachment = TherapyPostAttachment.create(
                    post,
                    storedFileInfo.getStoredPath(),
                    storedFileInfo.getOriginalFilename(),
                    storedFileInfo.getContentType(),
                    file.getSize(),
                    extractExtension(storedFileInfo.getOriginalFilename())
            );

            TherapyPostAttachment saved = therapyPostAttachmentRepository.save(attachment);
            post.updatePostType(PostType.RESOURCE);
            // 게시글 상세 응답과 동일하게 presigned URL을 박아 클라이언트가 S3 직접 GET하게 함.
            // 업로드 직후 시점은 다운로드 의도가 아니므로 audit log(recordDownload)는 호출하지 않음.
            String downloadUrl = fileStorageService.presignGet(saved.getStoredPath(), ATTACHMENT_PRESIGN_TTL);
            if (downloadUrl == null) {
                downloadUrl = "/api/v1/posts/" + post.getId()
                        + "/attachments/" + saved.getId() + "/download";
            }
            return PostAttachmentResponse.of(saved, downloadUrl);
        } catch (RuntimeException e) {
            safeDelete(storedFileInfo.getStoredPath(), currentUserId);
            throw e;
        }
    }

    /**
     * UploadConfirmService 가 권한/형식/한도 검증과 S3 copy/delete 를 마친 뒤 호출.
     * INSERT + PostType=RESOURCE 자동 전환.
     */
    @Transactional
    public PostAttachmentResponse confirmUpload(
            TherapyPost post,
            String storedPath,
            String originalFilename,
            String contentType,
            long sizeBytes
    ) {
        TherapyPostAttachment attachment = TherapyPostAttachment.create(
                post,
                storedPath,
                originalFilename,
                contentType,
                sizeBytes,
                extractExtension(originalFilename)
        );
        TherapyPostAttachment saved = therapyPostAttachmentRepository.save(attachment);
        post.updatePostType(PostType.RESOURCE);

        String downloadUrl = fileStorageService.presignGet(saved.getStoredPath(), ATTACHMENT_PRESIGN_TTL);
        if (downloadUrl == null) {
            downloadUrl = "/api/v1/posts/" + post.getId()
                    + "/attachments/" + saved.getId() + "/download";
        }
        return PostAttachmentResponse.of(saved, downloadUrl);
    }

    @Transactional
    public StoredFileResource downloadAttachment(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId,
            Long attachmentId
    ) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole, currentUserId);
        TherapyPostAttachment attachment = therapyPostAttachmentRepository.findByIdAndPostId(attachmentId, postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_ATTACHMENT_NOT_FOUND));

        StoredFileResource storedFile = fileStorageService.loadAsResource(
                attachment.getStoredPath(),
                attachment.getContentType(),
                attachment.getOriginalFilename()
        );

        recordDownload(post, user);

        userEventPublisher.publish(
                currentUserId,
                UserEventType.ATTACHMENT_DOWNLOAD,
                EventTargetType.POST,
                postId,
                Map.of(
                        "attachmentId", attachmentId,
                        "extension", attachment.getExtension() == null ? "" : attachment.getExtension(),
                        "sizeBytes", attachment.getSizeBytes()
                )
        );

        return storedFile;
    }

    @Transactional
    public void deleteAttachment(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId,
            Long attachmentId
    ) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole, currentUserId);
        resourceAccessValidator.validateAuthorOrAdmin(post.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.POST_ACCESS_DENIED);

        TherapyPostAttachment attachment = therapyPostAttachmentRepository.findByIdAndPostId(attachmentId, postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_ATTACHMENT_NOT_FOUND));

        String storedPath = attachment.getStoredPath();
        therapyPostAttachmentRepository.delete(attachment);

        boolean hasRemainingAttachments = !therapyPostAttachmentRepository.findByPostIdOrderByCreatedAtAsc(postId).isEmpty();
        if (!hasRemainingAttachments) {
            post.updatePostType(PostType.COMMUNITY);
        }

        safeDelete(storedPath, currentUserId);
    }

    public PagedResponse<DownloadedPostResponse> getMyDownloads(Long currentUserId, UserRole currentUserRole, int page, int size) {
        userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("lastDownloadedAt"), Sort.Order.desc("id"))
        );

        Page<TherapyPostDownload> result = visibilityPolicy.canViewPrivate(currentUserRole)
                ? therapyPostDownloadRepository.findByUserIdAndPost_DeletedAtIsNull(currentUserId, pageable)
                : therapyPostDownloadRepository.findByUserIdAndPost_DeletedAtIsNullAndPost_Visibility(currentUserId, Visibility.PUBLIC, pageable);

        return PagedResponse.from(result, result.getContent().stream()
                        .map(DownloadedPostResponse::from)
                        .toList());
    }

    /**
     * 게시글 상세 응답 빌드 시점에 첨부파일 목록을 prepare.
     * 호출처(PostService)가 이미 게시글 visibility/access 가드를 통과한 후 호출한다고 전제.
     *
     * 디자인 결정 — "presigned URL 발급 == 다운로드 의도로 간주":
     * - downloadUrl을 S3 presigned URL로 발급해 클라이언트가 S3 직접 GET (백엔드 byte 운반 0)
     * - 첨부가 있으면 발급 시점에 download history INSERT/update (idempotent)
     * - "보기 != 다운로드" 정확도는 약간 손해보지만 단순성/추적성 균형을 위한 trade-off
     *
     * 첨부 없으면 audit 호출도 안 함 → 빈 게시글 보기엔 노이즈 없음.
     */
    @Transactional
    public List<PostAttachmentResponse> getAttachmentsForPostUnchecked(TherapyPost post, Long currentUserId) {
        List<TherapyPostAttachment> attachments =
                therapyPostAttachmentRepository.findByPostIdOrderByCreatedAtAsc(post.getId());
        if (attachments.isEmpty()) {
            return List.of();
        }
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        recordDownload(post, user);
        return attachments.stream()
                .map(att -> {
                    String url = fileStorageService.presignGet(att.getStoredPath(), ATTACHMENT_PRESIGN_TTL);
                    if (url == null) {
                        // Local 환경 fallback (S3 미사용)
                        url = "/api/v1/posts/" + post.getId()
                                + "/attachments/" + att.getId() + "/download";
                    }
                    return PostAttachmentResponse.of(att, url);
                })
                .toList();
    }

    private void recordDownload(TherapyPost post, User user) {
        therapyPostDownloadRepository.findByPostIdAndUserId(post.getId(), user.getId())
                .ifPresentOrElse(
                        TherapyPostDownload::recordDownload,
                        () -> therapyPostDownloadRepository.save(TherapyPostDownload.create(post, user))
                );
    }

    private void safeDelete(String storedPath, Long userId) {
        try {
            fileStorageService.delete(storedPath);
        } catch (Exception e) {
            log.warn("{} userId={} storedPath={}", EVT_ATTACHMENT_DELETE_FAILED, userId, storedPath, e);
        }
    }

    private String extractExtension(String originalFilename) {
        int index = originalFilename.lastIndexOf('.');
        if (index < 0 || index == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(index + 1).toLowerCase();
    }
}
