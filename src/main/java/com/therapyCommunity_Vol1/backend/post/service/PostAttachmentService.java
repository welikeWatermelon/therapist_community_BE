package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostAttachmentService {

    private static final String EVT_ATTACHMENT_DELETE_FAILED = "POST_ATTACHMENT_DELETE_FAILED";

    private final ActivePostFinder activePostFinder;
    private final TherapyPostAttachmentRepository therapyPostAttachmentRepository;
    private final TherapyPostDownloadRepository therapyPostDownloadRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public PostAttachmentResponse uploadAttachment(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId,
            MultipartFile file
    ) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        validateAuthorOrAdmin(post, currentUserId, currentUserRole);

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
            return PostAttachmentResponse.from(saved);
        } catch (RuntimeException e) {
            safeDelete(storedFileInfo.getStoredPath(), currentUserId);
            throw e;
        }
    }

    @Transactional
    public StoredFileResource downloadAttachment(
            Long currentUserId,
            Long postId,
            Long attachmentId
    ) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        TherapyPost post = activePostFinder.findOrThrow(postId);
        TherapyPostAttachment attachment = therapyPostAttachmentRepository.findByIdAndPostId(attachmentId, postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_ATTACHMENT_NOT_FOUND));

        StoredFileResource storedFile = fileStorageService.loadAsResource(
                attachment.getStoredPath(),
                attachment.getContentType(),
                attachment.getOriginalFilename()
        );

        recordDownload(post, user);
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
        validateAuthorOrAdmin(post, currentUserId, currentUserRole);

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

    public PagedResponse<DownloadedPostResponse> getMyDownloads(Long currentUserId, int page, int size) {
        userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("lastDownloadedAt"), Sort.Order.desc("id"))
        );

        Page<TherapyPostDownload> result =
                therapyPostDownloadRepository.findByUserIdAndPost_DeletedAtIsNull(currentUserId, pageable);

        return PagedResponse.from(result, result.getContent().stream()
                        .map(DownloadedPostResponse::from)
                        .toList());
    }

    private void recordDownload(TherapyPost post, User user) {
        therapyPostDownloadRepository.findByPostIdAndUserId(post.getId(), user.getId())
                .ifPresentOrElse(
                        TherapyPostDownload::recordDownload,
                        () -> therapyPostDownloadRepository.save(TherapyPostDownload.create(post, user))
                );
    }

    private void validateAuthorOrAdmin(
            TherapyPost post,
            Long currentUserId,
            UserRole currentUserRole
    ) {
        boolean isAdmin = currentUserRole == UserRole.ADMIN;
        boolean isAuthor = post.getAuthor().getId().equals(currentUserId);

        if (!isAdmin && !isAuthor) {
            throw new CustomException(ErrorCode.POST_ACCESS_DENIED);
        }
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
