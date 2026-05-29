package com.therapyCommunity_Vol1.backend.user.service;

import com.therapyCommunity_Vol1.backend.auth.service.TokenService;
import com.therapyCommunity_Vol1.backend.global.cache.UserCacheService;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.therapist.dto.TherapistVerificationStatusDto;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.dto.CurrentUserResponse;
import com.therapyCommunity_Vol1.backend.user.dto.UpdateProfileRequest;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import com.therapyCommunity_Vol1.backend.user.support.ProfileImageUrlAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final FileStorageService fileStorageService;
    private final UserCacheService userCacheService;
    private final ProfileImageUrlAssembler profileImageUrlAssembler;

    public CurrentUserResponse getCurrentUser(Long currentUserId,
                                              Optional<TherapistVerificationStatusDto> verification,
                                              long followerCount, long followingCount) {
        User user = findUserOrThrow(currentUserId);

        return CurrentUserResponse.from(
                user,
                verification,
                profileImageUrlAssembler,
                followerCount,
                followingCount
        );
    }

    @Transactional
    public String uploadProfileImage(Long currentUserId, MultipartFile file) {
        User user = findUserOrThrow(currentUserId);
        StoredFileInfo storedFileInfo = fileStorageService.storeProfileImage(file);
        // storedFileInfo.getStoredPath() 는 "profile-images/abc.jpg" 형태 → 파일명만 추출해 저장
        String storageKey = profileImageUrlAssembler.toStorageKey(storedFileInfo.getStoredPath());
        user.updateProfile(null, storageKey);
        userCacheService.evict(currentUserId);
        return profileImageUrlAssembler.toFullUrl(storageKey);
    }

    public StoredFileResource loadProfileImage(String filename) {
        return fileStorageService.loadAsResource(
                profileImageUrlAssembler.toStoragePath(filename),
                "image/jpeg",
                filename
        );
    }

    @Transactional
    public CurrentUserResponse updateProfile(Long currentUserId, UpdateProfileRequest request,
                                             Optional<TherapistVerificationStatusDto> verification,
                                             long followerCount, long followingCount) {
        User user = findUserOrThrow(currentUserId);

        if (request.getNickname() != null
                && userRepository.existsByNicknameAndIdNot(request.getNickname(), currentUserId)) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_USED);
        }

        user.updateProfile(request.getNickname(), null);
        userCacheService.evict(currentUserId);

        return CurrentUserResponse.from(
                user,
                verification,
                profileImageUrlAssembler,
                followerCount,
                followingCount
        );
    }

    @Transactional
    public void withdraw(Long currentUserId) {
        User user = findUserOrThrow(currentUserId);
        user.withdraw();
        userCacheService.evict(currentUserId);  // 탈퇴 → 캐시 무효화 (isEnabled 체크 즉시 반영)

        tokenService.revokeAllForUser(currentUserId);
    }

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    public User findByIdOrNull(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).orElse(null);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    public User getReferenceById(Long userId) {
        return userRepository.getReferenceById(userId);
    }

    public List<Long> findUserIdsByRole(UserRole role) {
        return userRepository.findIdsByRole(role);
    }

    public List<User> findUsersByIds(List<Long> userIds) {
        return userRepository.findAllById(userIds);
    }

    private User findUserOrThrow(Long userId) {
        return findById(userId);
    }
}
