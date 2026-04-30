package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPostImage;
import com.therapyCommunity_Vol1.backend.post.dto.PostImageResponse;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostImageRepository;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostImageService {

    private static final Duration PRESIGN_TTL = Duration.ofHours(1);

    private final ActivePostFinder activePostFinder;
    private final TherapyPostImageRepository therapyPostImageRepository;
    private final FileStorageService fileStorageService;
    private final ResourceAccessValidator resourceAccessValidator;
    private final PostVisibilityAccessPolicy visibilityPolicy;

    @Transactional
    public PostImageResponse uploadImage(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId,
            MultipartFile file
    ) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole);
        resourceAccessValidator.validateAuthorOrAdmin(post.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.POST_ACCESS_DENIED);

        StoredFileInfo storedFileInfo = fileStorageService.storePostImage(file);
        int nextOrder = therapyPostImageRepository.countByPostId(postId);

        TherapyPostImage image = TherapyPostImage.create(
                post,
                storedFileInfo.getStoredPath(),
                storedFileInfo.getOriginalFilename(),
                storedFileInfo.getContentType(),
                file.getSize(),
                nextOrder
        );

        TherapyPostImage saved = therapyPostImageRepository.save(image);
        return toResponse(saved);
    }

    public List<PostImageResponse> getImages(Long postId, UserRole currentUserRole) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole);

        return therapyPostImageRepository.findByPostIdOrderByDisplayOrderAsc(postId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private PostImageResponse toResponse(TherapyPostImage image) {
        String presignedUrl = fileStorageService.presignGet(image.getStoredPath(), PRESIGN_TTL);
        if (presignedUrl != null) {
            return PostImageResponse.of(image, presignedUrl);
        }
        return PostImageResponse.from(image);
    }

    public StoredFileResource loadImage(Long postId, Long imageId, UserRole currentUserRole) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole);

        TherapyPostImage image = therapyPostImageRepository.findById(imageId)
                .filter(i -> i.getPost().getId().equals(postId))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        return fileStorageService.loadAsResource(
                image.getStoredPath(),
                image.getContentType(),
                image.getOriginalFilename()
        );
    }

    @Transactional
    public void deleteImage(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId,
            Long imageId
    ) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole);
        resourceAccessValidator.validateAuthorOrAdmin(post.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.POST_ACCESS_DENIED);

        TherapyPostImage image = therapyPostImageRepository.findById(imageId)
                .filter(i -> i.getPost().getId().equals(postId))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        String storedPath = image.getStoredPath();
        therapyPostImageRepository.delete(image);
        therapyPostImageRepository.flush();

        reassignDisplayOrder(postId);

        scheduleStorageDeleteAfterCommit(storedPath, postId, imageId);
    }

    /**
     * DB 트랜잭션 커밋 이후에 스토리지 파일 삭제를 실행.
     * 커밋 실패로 DB가 롤백되면 afterCommit은 호출되지 않아 orphan file이 생기지 않음.
     * 파일 삭제 실패는 best-effort로 로깅만 — DB는 이미 커밋됐으므로 복구 불가.
     * 트랜잭션이 활성화되지 않은 컨텍스트(단위 테스트 등)에서는 즉시 실행.
     */
    private void scheduleStorageDeleteAfterCommit(String storedPath, Long postId, Long imageId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteStoredFileBestEffort(storedPath, postId, imageId);
                }
            });
        } else {
            deleteStoredFileBestEffort(storedPath, postId, imageId);
        }
    }

    private void deleteStoredFileBestEffort(String storedPath, Long postId, Long imageId) {
        try {
            fileStorageService.delete(storedPath);
        } catch (Exception e) {
            log.warn("Failed to delete image file from storage. postId={}, imageId={}, storedPath={}", postId, imageId, storedPath, e);
        }
    }

    private void reassignDisplayOrder(Long postId) {
        List<TherapyPostImage> remaining = therapyPostImageRepository.findByPostIdOrderByDisplayOrderAsc(postId);
        for (int i = 0; i < remaining.size(); i++) {
            TherapyPostImage img = remaining.get(i);
            if (img.getDisplayOrder() != i) {
                img.updateDisplayOrder(i);
            }
        }
    }

}
