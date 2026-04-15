package com.therapyCommunity_Vol1.backend.user.service;

import com.therapyCommunity_Vol1.backend.auth.service.TokenService;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.cache.UserCacheService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.therapist.dto.TherapistVerificationStatusDto;
import com.therapyCommunity_Vol1.backend.therapist.service.TherapistVerificationService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.dto.CurrentUserResponse;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import com.therapyCommunity_Vol1.backend.user.support.ProfileImageUrlAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private TherapistVerificationService therapistVerificationService;
    private TokenService tokenService;
    private FileStorageService fileStorageService;
    private UserCacheService userCacheService;
    private ProfileImageUrlAssembler profileImageUrlAssembler;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        therapistVerificationService = mock(TherapistVerificationService.class);
        tokenService = mock(TokenService.class);
        fileStorageService = mock(FileStorageService.class);
        userCacheService = mock(UserCacheService.class);
        profileImageUrlAssembler = new ProfileImageUrlAssembler("http://localhost:8080");
        userService = new UserService(
                userRepository,
                therapistVerificationService,
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
        when(therapistVerificationService.findVerificationStatusByUserId(1L)).thenReturn(Optional.empty());

        CurrentUserResponse response = userService.getCurrentUser(1L);

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
        when(therapistVerificationService.findVerificationStatusByUserId(1L)).thenReturn(Optional.of(statusDto));

        CurrentUserResponse response = userService.getCurrentUser(1L);

        assertThat(response.therapistVerification().status()).isEqualTo("PENDING");
        assertThat(response.therapistVerification().requestedAt()).isEqualTo(requestedAt);
        assertThat(response.therapistVerification().reviewedAt()).isNull();
    }

    @Test
    void 현재_유저가_없으면_예외를_던진다() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> userService.getCurrentUser(99L));

        assertThat(thrown).isInstanceOf(CustomException.class);
        assertThat(((CustomException) thrown).getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
