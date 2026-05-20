package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPostVideo;
import com.therapyCommunity_Vol1.backend.post.dto.PostVideoResponse;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostVideoRepository;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostVideoService {

    private static final Duration PRESIGN_TTL = Duration.ofHours(1);

    private final ActivePostFinder activePostFinder;
    private final TherapyPostVideoRepository therapyPostVideoRepository;
    private final FileStorageService fileStorageService;
    private final ResourceAccessValidator resourceAccessValidator;
    private final PostVisibilityAccessPolicy visibilityPolicy;

    /**
     * 게시글 상세 빌드 시점에 영상 목록을 prepare. 호출처가 visibility/access 가드 통과 후 호출.
     */
    public List<PostVideoResponse> getVideosForPostUnchecked(Long postId) {
        return therapyPostVideoRepository.findByPostIdOrderByCreatedAtAsc(postId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public StoredFileResource loadVideo(Long postId, Long videoId, UserRole currentUserRole) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole);

        TherapyPostVideo video = therapyPostVideoRepository.findByIdAndPostId(videoId, postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_VIDEO_NOT_FOUND));

        return fileStorageService.loadAsResource(
                video.getStoredPath(),
                video.getContentType(),
                video.getOriginalFilename()
        );
    }

    @Transactional
    public void deleteVideo(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId,
            Long videoId
    ) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole);
        resourceAccessValidator.validateAuthorOrAdmin(post.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.POST_ACCESS_DENIED);

        TherapyPostVideo video = therapyPostVideoRepository.findByIdAndPostId(videoId, postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_VIDEO_NOT_FOUND));

        String storedPath = video.getStoredPath();
        String thumbnailPath = video.getThumbnailPath();
        therapyPostVideoRepository.delete(video);

        scheduleStorageDeleteAfterCommit(storedPath, postId, videoId);
        if (thumbnailPath != null) {
            scheduleStorageDeleteAfterCommit(thumbnailPath, postId, videoId);
        }
    }

    /**
     * UploadConfirmService 가 권한/형식 검증 + S3 copy/delete 후 호출. 단순 INSERT.
     */
    @Transactional
    public PostVideoResponse confirmUpload(
            TherapyPost post,
            String storedPath,
            String originalFilename,
            String contentType,
            long sizeBytes,
            Integer durationSeconds
    ) {
        TherapyPostVideo video = TherapyPostVideo.create(
                post,
                storedPath,
                originalFilename,
                contentType,
                sizeBytes,
                durationSeconds
        );
        TherapyPostVideo saved = therapyPostVideoRepository.save(video);
        return toResponse(saved);
    }

    private PostVideoResponse toResponse(TherapyPostVideo video) {
        String videoUrl = fileStorageService.presignGet(video.getStoredPath(), PRESIGN_TTL);
        if (videoUrl == null) {
            videoUrl = "/api/v1/posts/" + video.getPost().getId() + "/videos/" + video.getId();
        }
        String thumbnailUrl = null;
        if (video.getThumbnailPath() != null) {
            thumbnailUrl = fileStorageService.presignGet(video.getThumbnailPath(), PRESIGN_TTL);
        }
        return PostVideoResponse.of(video, videoUrl, thumbnailUrl);
    }

    private void scheduleStorageDeleteAfterCommit(String storedPath, Long postId, Long videoId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteStoredFileBestEffort(storedPath, postId, videoId);
                }
            });
        } else {
            deleteStoredFileBestEffort(storedPath, postId, videoId);
        }
    }

    private void deleteStoredFileBestEffort(String storedPath, Long postId, Long videoId) {
        try {
            fileStorageService.delete(storedPath);
        } catch (Exception e) {
            log.warn("Failed to delete video file from storage. postId={}, videoId={}, storedPath={}", postId, videoId, storedPath, e);
        }
    }
}
