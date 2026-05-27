package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
        if (post.getPostType() == PostType.CONCERN_CARD) {
            throw new CustomException(ErrorCode.CONCERN_CARD_UPLOAD_NOT_ALLOWED);
        }
        visibilityPolicy.checkAccess(post, currentUserRole, currentUserId);
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

    /**
     * UploadConfirmService 가 권한/형식/한도 검증과 S3 copy/delete 를 마친 뒤 호출.
     * 단순 INSERT + displayOrder 부여.
     */
    @Transactional
    public PostImageResponse confirmUpload(
            TherapyPost post,
            String storedPath,
            String originalFilename,
            String contentType,
            long sizeBytes
    ) {
        int nextOrder = therapyPostImageRepository.countByPostId(post.getId());
        TherapyPostImage image = TherapyPostImage.create(
                post,
                storedPath,
                originalFilename,
                contentType,
                sizeBytes,
                nextOrder
        );
        // saveAndFlush: stored_path 유니크 위반을 이 호출(=UploadConfirmService try-catch) 안에서
        // 동기적으로 터뜨린다. IDENTITY 전략이라 어차피 즉시 INSERT 되지만, 전략이 바뀌어도(SEQUENCE 등)
        // 타이밍이 commit 으로 밀리지 않도록 명시적으로 flush.
        TherapyPostImage saved = therapyPostImageRepository.saveAndFlush(image);
        return toResponse(saved);
    }

    /**
     * 멱등 confirm 판정용 — finalKey(stored_path) 로 이미 영속된 이미지가 있으면 응답으로 매핑.
     * UploadConfirmService 가 재시도 단락에 사용.
     */
    public Optional<PostImageResponse> findByStoredPath(String storedPath) {
        return therapyPostImageRepository.findByStoredPath(storedPath).map(this::toResponse);
    }

    public List<PostImageResponse> getImages(Long postId, UserRole currentUserRole, Long currentUserId) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole, currentUserId);

        return getImagesForPostUnchecked(postId);
    }

    /**
     * 게시글 응답 빌드 시점에 이미지 정보를 함께 박을 때 사용 — 권한 체크 없이 단순 조회.
     * 호출처(PostService 등)가 이미 게시글에 대한 visibility/access 가드를 통과한 후 호출한다고 전제.
     */
    public List<PostImageResponse> getImagesForPostUnchecked(Long postId) {
        return therapyPostImageRepository.findByPostIdOrderByDisplayOrderAsc(postId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 목록 응답 빌드 시점의 N+1 제거용 batch 조회.
     * 호출처가 권한 따라 visiblePostIds를 미리 필터해서 넘기므로 권한 가드 없이 단순 조회.
     * 빈 입력은 빈 맵 반환(SQL 실행 X).
     */
    public Map<Long, List<PostImageResponse>> getImagesByPostIds(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return therapyPostImageRepository
                .findByPostIdInOrderByPostIdAscDisplayOrderAsc(postIds)
                .stream()
                .collect(Collectors.groupingBy(
                        img -> img.getPost().getId(),
                        Collectors.mapping(this::toResponse, Collectors.toList())
                ));
    }

    private PostImageResponse toResponse(TherapyPostImage image) {
        String presignedUrl = fileStorageService.presignGet(image.getStoredPath(), PRESIGN_TTL);
        if (presignedUrl != null) {
            return PostImageResponse.of(image, presignedUrl);
        }
        return PostImageResponse.from(image);
    }

    public StoredFileResource loadImage(Long postId, Long imageId, UserRole currentUserRole, Long currentUserId) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole, currentUserId);

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
        visibilityPolicy.checkAccess(post, currentUserRole, currentUserId);
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
