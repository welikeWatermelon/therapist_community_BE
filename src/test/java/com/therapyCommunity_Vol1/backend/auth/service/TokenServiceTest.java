package com.therapyCommunity_Vol1.backend.auth.service;

import com.therapyCommunity_Vol1.backend.auth.domain.RefreshToken;
import com.therapyCommunity_Vol1.backend.auth.repository.RefreshTokenRepository;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.security.JwtTokenProvider;
import com.therapyCommunity_Vol1.backend.therapist.dto.TherapistVerificationStatusDto;
import com.therapyCommunity_Vol1.backend.therapist.service.TherapistVerificationService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.support.ProfileImageUrlAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenServiceTest {

    JwtTokenProvider jwtTokenProvider;
    RefreshTokenRepository refreshTokenRepository;
    RefreshTokenManager refreshTokenManager;
    TherapistVerificationService therapistVerificationService;
    FileStorageService fileStorageService;
    ProfileImageUrlAssembler profileImageUrlAssembler;

    TokenService tokenService;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        refreshTokenManager = mock(RefreshTokenManager.class);
        therapistVerificationService = mock(TherapistVerificationService.class);
        fileStorageService = mock(FileStorageService.class);
        profileImageUrlAssembler = new ProfileImageUrlAssembler("http://localhost:8080", fileStorageService);

        tokenService = new TokenService(
                jwtTokenProvider,
                refreshTokenRepository,
                refreshTokenManager,
                therapistVerificationService,
                profileImageUrlAssembler
        );
        ReflectionTestUtils.setField(tokenService, "refreshTokenTtlSec", 1209600L);
    }

    @Test
    void refresh_응답에_user_정보가_포함된다() {
        // given
        User user = User.builder()
                .id(7L)
                .email("user@example.com")
                .passwordHash("encoded")
                .nickname("라쿤#4032")
                .role(UserRole.THERAPIST)
                .profileImageUrl("abc-123.jpg")
                .build();
        UUID family = UUID.randomUUID();
        RefreshToken stored = RefreshToken.issue(
                user,
                "old-hash",
                family,
                "JUnit",
                "127.0.0.1",
                LocalDateTime.now().plusDays(7)
        );

        when(refreshTokenManager.hash("raw-refresh")).thenReturn("old-hash");
        when(refreshTokenManager.generateRawToken()).thenReturn("new-raw-refresh");
        when(refreshTokenManager.hash("new-raw-refresh")).thenReturn("new-hash");
        when(refreshTokenRepository.findByTokenHash("old-hash")).thenReturn(Optional.of(stored));
        when(jwtTokenProvider.createAccessToken(eq(7L), eq("THERAPIST"))).thenReturn("new-access-token");
        when(jwtTokenProvider.getAccessTokenValiditySec()).thenReturn(1800L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));
        when(therapistVerificationService.findVerificationStatusByUserId(7L))
                .thenReturn(Optional.of(new TherapistVerificationStatusDto(
                        "APPROVED",
                        LocalDateTime.of(2026, 3, 15, 9, 0),
                        LocalDateTime.of(2026, 3, 16, 9, 0),
                        null
                )));
        when(fileStorageService.presignGet(eq("profile-images/abc-123.jpg"), eq(Duration.ofHours(1))))
                .thenReturn("https://cdn.example.com/profile-images/abc-123.jpg?X-Amz-Signature=fresh");

        // when
        TokenService.RefreshResult result = tokenService.refresh("raw-refresh", "JUnit", "127.0.0.1");

        // then
        assertThat(result.response().accessToken()).isEqualTo("new-access-token");
        assertThat(result.response().accessTokenExpiresInSec()).isEqualTo(1800L);
        assertThat(result.response().user()).isNotNull();
        assertThat(result.response().user().id()).isEqualTo(7L);
        assertThat(result.response().user().email()).isEqualTo("user@example.com");
        assertThat(result.response().user().nickname()).isEqualTo("라쿤#4032");
        assertThat(result.response().user().role()).isEqualTo("THERAPIST");
        assertThat(result.response().user().canAccessCommunity()).isTrue();
        assertThat(result.response().user().profileImageUrl())
                .isEqualTo("https://cdn.example.com/profile-images/abc-123.jpg?X-Amz-Signature=fresh");
        assertThat(result.response().user().therapistVerification().status()).isEqualTo("APPROVED");
        assertThat(result.refreshToken()).isEqualTo("new-raw-refresh");
        assertThat(result.refreshTokenExpiresInSec()).isEqualTo(1209600L);
    }
}
