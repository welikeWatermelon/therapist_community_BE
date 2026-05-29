package com.therapyCommunity_Vol1.backend.user.service;

import com.therapyCommunity_Vol1.backend.auth.service.TokenService;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.cache.UserCacheService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.therapist.dto.TherapistVerificationStatusDto;

import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.user.dto.CurrentUserResponse;
import com.therapyCommunity_Vol1.backend.user.dto.UpdateProfileRequest;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import com.therapyCommunity_Vol1.backend.user.support.ProfileImageUrlAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private TokenService tokenService;
    private FileStorageService fileStorageService;
    private UserCacheService userCacheService;
    private ProfileImageUrlAssembler profileImageUrlAssembler;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenService = mock(TokenService.class);
        fileStorageService = mock(FileStorageService.class);
        userCacheService = mock(UserCacheService.class);
        profileImageUrlAssembler = new ProfileImageUrlAssembler("http://localhost:8080", fileStorageService);
        userService = new UserService(
                userRepository,
                tokenService,
                fileStorageService,
                userCacheService,
                profileImageUrlAssembler
        );
    }

    @Test
    void 현재_유저를_조회하면_신청이력이_없을때_NOT_REQUESTED를_반환한다() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .nickname("tester")
                .role(UserRole.USER)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        CurrentUserResponse response = userService.getCurrentUser(1L, Optional.empty(), 0, 0);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.canAccessCommunity()).isFalse();
        assertThat(response.therapistVerification().status()).isEqualTo("NOT_REQUESTED");
    }

    @Test
    void 현재_유저를_조회하면_치료사_인증_요약을_반환한다() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .nickname("tester")
                .role(UserRole.USER)
                .build();
        LocalDateTime requestedAt = LocalDateTime.of(2026, 3, 16, 10, 0);
        TherapistVerificationStatusDto statusDto = new TherapistVerificationStatusDto(
                "PENDING", requestedAt, null, null
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        CurrentUserResponse response = userService.getCurrentUser(1L, Optional.of(statusDto), 0, 0);

        assertThat(response.therapistVerification().status()).isEqualTo("PENDING");
        assertThat(response.therapistVerification().requestedAt()).isEqualTo(requestedAt);
        assertThat(response.therapistVerification().reviewedAt()).isNull();
    }

    @Test
    void 현재_유저가_없으면_예외를_던진다() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> userService.getCurrentUser(99L, Optional.empty(), 0, 0));

        assertThat(thrown).isInstanceOf(CustomException.class);
        assertThat(((CustomException) thrown).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 프로필_이미지_업로드시_DB에는_파일명만_저장되고_응답은_풀_URL로_나간다() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .nickname("tester")
                .role(UserRole.USER)
                .build();
        MultipartFile mockFile = mock(MultipartFile.class);
        // 스토리지 반환값은 "profile-images/{uuid}.jpg" 형태 — 디렉토리 prefix 포함
        StoredFileInfo storedFileInfo = new StoredFileInfo(
                "profile-images/abc-123.jpg", "orig.jpg", "image/jpeg"
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fileStorageService.storeProfileImage(mockFile)).thenReturn(storedFileInfo);

        String returnedUrl = userService.uploadProfileImage(1L, mockFile);

        // DB에는 파일명만 (디렉토리 prefix 제거됨)
        assertThat(user.getProfileImageUrl()).isEqualTo("abc-123.jpg");
        // 응답은 풀 URL 로 조립됨
        assertThat(returnedUrl).isEqualTo("http://localhost:8080/api/v1/me/profile-image/abc-123.jpg");
        // 캐시 무효화 호출 확인
        verify(userCacheService).evict(1L);
    }

    @Test
    void 닉네임만_수정하면_프로필_이미지는_그대로_유지된다() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .nickname("oldNick")
                .role(UserRole.USER)
                .build();
        ReflectionTestUtils.setField(user, "profileImageUrl", "existing.jpg");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByNicknameAndIdNot("newNick", 1L)).thenReturn(false);

        UpdateProfileRequest request = new UpdateProfileRequest("newNick");
        userService.updateProfile(1L, request, Optional.empty(), 0, 0);

        assertThat(user.getNickname()).isEqualTo("newNick");
        // 프로필 이미지 변경 경로는 PATCH 에서 제거됨 → 기존 값 그대로
        assertThat(user.getProfileImageUrl()).isEqualTo("existing.jpg");
    }
}
