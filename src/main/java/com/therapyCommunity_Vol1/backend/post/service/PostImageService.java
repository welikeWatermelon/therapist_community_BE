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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostImageService {

    private final ActivePostFinder activePostFinder;
    private final TherapyPostImageRepository therapyPostImageRepository;
    private final FileStorageService fileStorageService;
    private final ResourceAccessValidator resourceAccessValidator;

    @Transactional
    public PostImageResponse uploadImage(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId,
            MultipartFile file
    ) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        resourceAccessValidator.validateAuthorOrAdmin(post.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.POST_ACCESS_DENIED);

        StoredFileInfo storedFileInfo = fileStorageService.storeProfileImage(file);
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
        return PostImageResponse.from(saved);
    }

    public List<PostImageResponse> getImages(Long postId) {
        return therapyPostImageRepository.findByPostIdOrderByDisplayOrderAsc(postId)
                .stream()
                .map(PostImageResponse::from)
                .toList();
    }

    public StoredFileResource loadImage(Long postId, Long imageId) {
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
        resourceAccessValidator.validateAuthorOrAdmin(post.getAuthor().getId(), currentUserId, currentUserRole, ErrorCode.POST_ACCESS_DENIED);

        TherapyPostImage image = therapyPostImageRepository.findById(imageId)
                .filter(i -> i.getPost().getId().equals(postId))
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        String storedPath = image.getStoredPath();
        therapyPostImageRepository.delete(image);
        therapyPostImageRepository.flush();

        reassignDisplayOrder(postId);

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
