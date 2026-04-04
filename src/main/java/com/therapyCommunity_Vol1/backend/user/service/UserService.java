package com.therapyCommunity_Vol1.backend.user.service;

import com.therapyCommunity_Vol1.backend.auth.repository.RefreshTokenRepository;
import com.therapyCommunity_Vol1.backend.comment.repository.TherapyPostCommentRepository;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.post.dto.PostListResponse;
import com.therapyCommunity_Vol1.backend.post.dto.TherapyPostSummaryResponse;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.therapist.service.TherapistVerificationService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.dto.CurrentUserResponse;
import com.therapyCommunity_Vol1.backend.user.dto.MyCommentListResponse;
import com.therapyCommunity_Vol1.backend.user.dto.MyCommentResponse;
import com.therapyCommunity_Vol1.backend.user.dto.UpdateProfileRequest;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final TherapistVerificationService therapistVerificationService;
    private final TherapyPostRepository therapyPostRepository;
    private final TherapyPostCommentRepository therapyPostCommentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FileStorageService fileStorageService;

    public CurrentUserResponse getCurrentUser(Long currentUserId) {
        User user = findUserOrThrow(currentUserId);

        return CurrentUserResponse.from(
                user,
                therapistVerificationService.findByUserId(currentUserId)
        );
    }

    public PostListResponse getMyPosts(Long currentUserId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        var result = therapyPostRepository.findByAuthorIdAndDeletedAtIsNull(currentUserId, pageable);

        var posts = result.getContent()
                .stream()
                .map(TherapyPostSummaryResponse::from)
                .toList();

        return new PostListResponse(
                posts,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    public MyCommentListResponse getMyComments(Long currentUserId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        var result = therapyPostCommentRepository.findByAuthorId(currentUserId, pageable);

        var comments = result.getContent()
                .stream()
                .map(MyCommentResponse::from)
                .toList();

        return new MyCommentListResponse(
                comments,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional
    public String uploadProfileImage(Long currentUserId, MultipartFile file) {
        User user = findUserOrThrow(currentUserId);
        StoredFileInfo storedFileInfo = fileStorageService.storeProfileImage(file);
        String imageUrl = "/api/v1/me/profile-image/" + storedFileInfo.getStoredPath();
        user.updateProfile(null, imageUrl);
        return imageUrl;
    }

    @Transactional
    public CurrentUserResponse updateProfile(Long currentUserId, UpdateProfileRequest request) {
        User user = findUserOrThrow(currentUserId);

        if (request.getNickname() != null
                && userRepository.existsByNicknameAndIdNot(request.getNickname(), currentUserId)) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_USED);
        }

        user.updateProfile(request.getNickname(), request.getProfileImageUrl());

        return CurrentUserResponse.from(
                user,
                therapistVerificationService.findByUserId(currentUserId)
        );
    }

    @Transactional
    public void withdraw(Long currentUserId) {
        User user = findUserOrThrow(currentUserId);
        user.withdraw();

        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(currentUserId)
                .forEach(token -> token.revoke("WITHDRAW"));
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
