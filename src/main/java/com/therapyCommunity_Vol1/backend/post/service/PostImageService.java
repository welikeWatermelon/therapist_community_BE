package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
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

    @Transactional
    public PostImageResponse uploadImage(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId,
            MultipartFile file
    ) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        validateAuthorOrAdmin(post, currentUserId, currentUserRole);

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

    private void validateAuthorOrAdmin(TherapyPost post, Long currentUserId, UserRole currentUserRole) {
        boolean isAdmin = currentUserRole == UserRole.ADMIN;
        boolean isAuthor = post.getAuthor().getId().equals(currentUserId);
        if (!isAdmin && !isAuthor) {
            throw new CustomException(ErrorCode.POST_ACCESS_DENIED);
        }
    }
}
