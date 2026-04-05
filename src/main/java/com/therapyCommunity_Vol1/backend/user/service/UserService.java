package com.therapyCommunity_Vol1.backend.user.service;

import com.therapyCommunity_Vol1.backend.auth.service.TokenService;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.therapist.service.TherapistVerificationService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.dto.CurrentUserResponse;
import com.therapyCommunity_Vol1.backend.user.dto.UpdateProfileRequest;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final TherapistVerificationService therapistVerificationService;
    private final TokenService tokenService;
    private final FileStorageService fileStorageService;

    @Value("${app.base-url}")
    private String baseUrl;

    public CurrentUserResponse getCurrentUser(Long currentUserId) {
        User user = findUserOrThrow(currentUserId);

        return CurrentUserResponse.from(
                user,
                therapistVerificationService.findByUserId(currentUserId)
        );
    }

    @Transactional
    public String uploadProfileImage(Long currentUserId, MultipartFile file) {
        User user = findUserOrThrow(currentUserId);
        StoredFileInfo storedFileInfo = fileStorageService.storeProfileImage(file);
        String imageUrl = baseUrl + "/api/v1/me/profile-image/" + storedFileInfo.getStoredPath();
        user.updateProfile(null, imageUrl);
        return imageUrl;
    }

    public StoredFileResource loadProfileImage(String filename) {
        return fileStorageService.loadAsResource(
                "profile-images/" + filename,
                "image/jpeg",
                filename
        );
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

        tokenService.revokeAllForUser(currentUserId);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
