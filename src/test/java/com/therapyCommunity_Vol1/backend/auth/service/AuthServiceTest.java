package com.therapyCommunity_Vol1.backend.auth.service;

import com.therapyCommunity_Vol1.backend.auth.domain.RefreshToken;
import com.therapyCommunity_Vol1.backend.auth.dto.LoginRequest;
import com.therapyCommunity_Vol1.backend.auth.dto.SignupRequest;
import com.therapyCommunity_Vol1.backend.auth.dto.SignupResponse;
import com.therapyCommunity_Vol1.backend.auth.repository.RefreshTokenRepository;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.JwtTokenProvider;
import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerification;
import com.therapyCommunity_Vol1.backend.therapist.service.TherapistVerificationService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class

AuthServiceTest {

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    JwtTokenProvider jwtTokenProvider;
    RefreshTokenRepository refreshTokenRepository;
    RefreshTokenManager refreshTokenManager;
    TherapistVerificationService therapistVerificationService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        refreshTokenManager = mock(RefreshTokenManager.class);
        therapistVerificationService = mock(TherapistVerificationService.class);

        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenRepository,
                refreshTokenManager,
                therapistVerificationService
        );
        ReflectionTestUtils.setField(authService, "refreshTokenTtlSec", 1209600L);
    }

    @Test
    void 로그인_성공시_access_응답과_refresh_cookie용_토큰을_발급하고_refresh_해시를_저장한다() {
        // given
        LoginRequest request = new LoginRequest("test@test.com", "1234");
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
                .passwordHash("encoded")
                .nickname("tester")
                .role(UserRole.USER)
                .build();

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("1234", "encoded")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(1L, "USER")).thenReturn("jwt-token");
        when(jwtTokenProvider.getAccessTokenValiditySec()).thenReturn(1800L);
        when(refreshTokenManager.generateRawToken()).thenReturn("raw-refresh-token");
        when(refreshTokenManager.hash("raw-refresh-token")).thenReturn("hashed-refresh-token");
        when(therapistVerificationService.findByUserId(1L)).thenReturn(Optional.empty());
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        AuthService.LoginResult result = authService.login(request, "Mozilla/5.0", "127.0.0.1");

        // then
        assertThat(result.refreshToken()).isEqualTo("raw-refresh-token");
        assertThat(result.refreshTokenExpiresInSec()).isEqualTo(1209600L);
        assertThat(result.response().isNewUser()).isFalse();
        assertThat(result.response().tokens().accessToken()).isEqualTo("jwt-token");
        assertThat(result.response().tokens().accessTokenExpiresInSec()).isEqualTo(1800L);
        assertThat(result.response().user().email()).isEqualTo("test@test.com");
        assertThat(result.response().user().nickname()).isEqualTo("tester");
        assertThat(result.response().user().canAccessCommunity()).isFalse();
        assertThat(result.response().user().therapistVerification().status()).isEqualTo("NOT_REQUESTED");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken savedToken = captor.getValue();

        assertThat(savedToken.getUser()).isEqualTo(user);
        assertThat(savedToken.getTokenHash()).isEqualTo("hashed-refresh-token");
        assertThat(savedToken.getTokenFamily()).isNotNull();
        assertThat(savedToken.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(savedToken.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(savedToken.isRevoked()).isFalse();
    }

    @Test
    void 로그인_응답에_치료사_인증요약을_포함한다() {
        LoginRequest request = new LoginRequest("test@test.com", "1234");
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
                .passwordHash("encoded")
                .nickname("tester")
                .role(UserRole.USER)
                .build();
        TherapistVerification verification = TherapistVerification.create(
                user,
                "LIC-123",
                "/tmp/license.png",
                "license.png",
                "image/png"
        );
        LocalDateTime requestedAt = LocalDateTime.of(2026, 3, 15, 9, 0);
        ReflectionTestUtils.setField(verification, "createdAt", requestedAt);

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("1234", "encoded")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(1L, "USER")).thenReturn("jwt-token");
        when(jwtTokenProvider.getAccessTokenValiditySec()).thenReturn(1800L);
        when(refreshTokenManager.generateRawToken()).thenReturn("raw-refresh-token");
        when(refreshTokenManager.hash("raw-refresh-token")).thenReturn("hashed-refresh-token");
        when(therapistVerificationService.findByUserId(1L)).thenReturn(Optional.of(verification));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AuthService.LoginResult result = authService.login(request, "Mozilla/5.0", "127.0.0.1");

        assertThat(result.response().user().therapistVerification().status()).isEqualTo("PENDING");
        assertThat(result.response().user().therapistVerification().requestedAt()).isEqualTo(requestedAt);
        assertThat(result.response().user().therapistVerification().reviewedAt()).isNull();
        assertThat(result.response().user().therapistVerification().rejectionReason()).isNull();
    }

    @Test
    void 리프레시_성공시_기존토큰은_ROTATED_처리되고_같은_family로_새토큰이_발급된다() {
        // given
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
                .role(UserRole.USER)
                .build();
        UUID family = UUID.randomUUID();
        RefreshToken currentRefreshToken = RefreshToken.issue(
                user,
                "old-hash",
                family,
                "old-ua",
                "old-ip",
                LocalDateTime.now().plusDays(14)
        );

        when(refreshTokenManager.hash("old-raw-token")).thenReturn("old-hash");
        when(refreshTokenRepository.findByTokenHash("old-hash")).thenReturn(Optional.of(currentRefreshToken));
        when(jwtTokenProvider.createAccessToken(1L, "USER")).thenReturn("new-access-token");
        when(jwtTokenProvider.getAccessTokenValiditySec()).thenReturn(1800L);
        when(refreshTokenManager.generateRawToken()).thenReturn("new-raw-token");
        when(refreshTokenManager.hash("new-raw-token")).thenReturn("new-hash");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        AuthService.RefreshResult result = authService.refresh(
                "old-raw-token",
                "new-ua",
                "new-ip"
        );

        // then
        assertThat(currentRefreshToken.isRevoked()).isTrue();
        assertThat(currentRefreshToken.getRevokedReason()).isEqualTo("ROTATED");
        assertThat(result.response().accessToken()).isEqualTo("new-access-token");
        assertThat(result.response().accessTokenExpiresInSec()).isEqualTo(1800L);
        assertThat(result.refreshToken()).isEqualTo("new-raw-token");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken newToken = captor.getValue();
        assertThat(newToken.getTokenFamily()).isEqualTo(family);
        assertThat(newToken.getTokenHash()).isEqualTo("new-hash");
    }

    @Test
    void revoke된_리프레시토큰_재사용시_family_활성토큰_전체를_폐기한다() {
        // given
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
                .role(UserRole.USER)
                .build();
        UUID family = UUID.randomUUID();

        RefreshToken reusedToken = RefreshToken.issue(
                user,
                "reused-hash",
                family,
                "ua",
                "ip",
                LocalDateTime.now().plusDays(14)
        );
        reusedToken.revoke("ROTATED");

        RefreshToken activeToken1 = RefreshToken.issue(
                user,
                "active-hash-1",
                family,
                "ua-1",
                "ip-1",
                LocalDateTime.now().plusDays(14)
        );
        RefreshToken activeToken2 = RefreshToken.issue(
                user,
                "active-hash-2",
                family,
                "ua-2",
                "ip-2",
                LocalDateTime.now().plusDays(14)
        );

        when(refreshTokenManager.hash("reused-raw-token")).thenReturn("reused-hash");
        when(refreshTokenRepository.findByTokenHash("reused-hash")).thenReturn(Optional.of(reusedToken));
        when(refreshTokenRepository.findByTokenFamilyAndRevokedAtIsNull(family))
                .thenReturn(List.of(activeToken1, activeToken2));

        // when
        Throwable thrown = catchThrowable(() -> authService.refresh(
                "reused-raw-token",
                "ua-new",
                "ip-new"
        ));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        assertThat(((CustomException) thrown).getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
        assertThat(activeToken1.isRevoked()).isTrue();
        assertThat(activeToken1.getRevokedReason()).isEqualTo("REUSE_DETECTED");
        assertThat(activeToken2.isRevoked()).isTrue();
        assertThat(activeToken2.getRevokedReason()).isEqualTo("REUSE_DETECTED");
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void 만료된_리프레시토큰은_EXPIRED로_폐기되고_예외를_던진다() {
        // given
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
                .role(UserRole.USER)
                .build();
        RefreshToken expiredToken = RefreshToken.issue(
                user,
                "expired-hash",
                UUID.randomUUID(),
                "ua",
                "ip",
                LocalDateTime.now().minusSeconds(1)
        );

        when(refreshTokenManager.hash("expired-raw-token")).thenReturn("expired-hash");
        when(refreshTokenRepository.findByTokenHash("expired-hash")).thenReturn(Optional.of(expiredToken));

        // when
        Throwable thrown = catchThrowable(() -> authService.refresh(
                "expired-raw-token",
                "ua-new",
                "ip-new"
        ));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        assertThat(((CustomException) thrown).getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED);
        assertThat(expiredToken.isRevoked()).isTrue();
        assertThat(expiredToken.getRevokedReason()).isEqualTo("EXPIRED");
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void 로그아웃시_리프레시토큰을_폐기한다() {
        // given
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
                .role(UserRole.USER)
                .build();
        RefreshToken token = RefreshToken.issue(
                user,
                "logout-hash",
                UUID.randomUUID(),
                "ua",
                "ip",
                LocalDateTime.now().plusDays(14)
        );

        when(refreshTokenManager.hash("logout-raw-token")).thenReturn("logout-hash");
        when(refreshTokenRepository.findByTokenHash("logout-hash")).thenReturn(Optional.of(token));

        // when
        authService.logout("logout-raw-token");

        // then
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedReason()).isEqualTo("LOGOUT");
    }

    @Test
    void 회원가입_성공() {
        // given
        SignupRequest request = new SignupRequest("test@test.com", "1234", "tester");

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("1234")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User unsaved = invocation.getArgument(0);
            return User.builder()
                    .id(1L)
                    .email(unsaved.getEmail())
                    .passwordHash(unsaved.getPasswordHash())
                    .nickname(unsaved.getNickname())
                    .role(unsaved.getRole())
                    .build();
        });

        // when
        SignupResponse response = authService.signup(request);

        // then
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("test@test.com");
        verify(passwordEncoder).encode("1234");

        verify(userRepository).save(argThat(saved ->
                saved.getEmail().equals("test@test.com")
                        && saved.getPasswordHash().equals("encoded-password")
                        && saved.getNickname().equals("tester")
                        && saved.getRole() == UserRole.USER
        ));
    }

    @Test
    void 회원가입_실패_중복이메일() {
        // given
        SignupRequest request = new SignupRequest("test@test.com", "1234", "tester");
        User existingUser = User.builder()
                .id(99L)
                .email("test@test.com")
                .passwordHash("encoded")
                .nickname("exists")
                .role(UserRole.USER)
                .build();
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(existingUser));

        // when
        Throwable thrown = catchThrowable(() -> authService.signup(request));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        assertThat(((CustomException) thrown).getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
        verify(userRepository, never()).save(any(User.class));
    }
}
