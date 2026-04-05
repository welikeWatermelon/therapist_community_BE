package com.therapyCommunity_Vol1.backend.auth.service;

import com.therapyCommunity_Vol1.backend.auth.dto.AgreementRequest;
import com.therapyCommunity_Vol1.backend.auth.dto.LoginRequest;
import com.therapyCommunity_Vol1.backend.auth.dto.SignupRequest;
import com.therapyCommunity_Vol1.backend.auth.dto.SignupResponse;
import com.therapyCommunity_Vol1.backend.auth.repository.UserAgreementRepository;
import com.therapyCommunity_Vol1.backend.global.cache.LoginAttemptService;
import com.therapyCommunity_Vol1.backend.auth.support.NicknameGenerator;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerification;
import com.therapyCommunity_Vol1.backend.therapist.service.TherapistVerificationService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    TokenService tokenService;
    TherapistVerificationService therapistVerificationService;
    NicknameGenerator nicknameGenerator;
    UserAgreementRepository userAgreementRepository;
    LoginAttemptService loginAttemptService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        tokenService = mock(TokenService.class);
        therapistVerificationService = mock(TherapistVerificationService.class);
        nicknameGenerator = mock(NicknameGenerator.class);
        userAgreementRepository = mock(UserAgreementRepository.class);
        loginAttemptService = mock(LoginAttemptService.class);

        authService = new AuthService(
                userRepository,
                passwordEncoder,
                tokenService,
                therapistVerificationService,
                nicknameGenerator,
                userAgreementRepository,
                loginAttemptService
        );
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
        when(tokenService.createAccessToken(user)).thenReturn("jwt-token");
        when(tokenService.getAccessTokenValiditySec()).thenReturn(1800L);
        when(tokenService.issueRefreshToken(eq(user), any(UUID.class), eq("Mozilla/5.0"), eq("127.0.0.1")))
                .thenReturn(new TokenService.IssuedToken("raw-refresh-token", 1209600L));
        when(therapistVerificationService.findByUserId(1L)).thenReturn(Optional.empty());

        // when
        AuthService.LoginResult result = authService.login(request, "Mozilla/5.0", "127.0.0.1");

        // then
        assertThat(result.refreshToken()).isEqualTo("raw-refresh-token");
        assertThat(result.refreshTokenExpiresInSec()).isEqualTo(1209600L);
        assertThat(result.response().tokens().accessToken()).isEqualTo("jwt-token");
        assertThat(result.response().tokens().accessTokenExpiresInSec()).isEqualTo(1800L);
        assertThat(result.response().user().email()).isEqualTo("test@test.com");
        assertThat(result.response().user().nickname()).isEqualTo("tester");
        assertThat(result.response().user().canAccessCommunity()).isFalse();
        assertThat(result.response().user().therapistVerification().status()).isEqualTo("NOT_REQUESTED");
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
        when(tokenService.createAccessToken(user)).thenReturn("jwt-token");
        when(tokenService.getAccessTokenValiditySec()).thenReturn(1800L);
        when(tokenService.issueRefreshToken(eq(user), any(UUID.class), eq("Mozilla/5.0"), eq("127.0.0.1")))
                .thenReturn(new TokenService.IssuedToken("raw-refresh-token", 1209600L));
        when(therapistVerificationService.findByUserId(1L)).thenReturn(Optional.of(verification));

        AuthService.LoginResult result = authService.login(request, "Mozilla/5.0", "127.0.0.1");

        assertThat(result.response().user().therapistVerification().status()).isEqualTo("PENDING");
        assertThat(result.response().user().therapistVerification().requestedAt()).isEqualTo(requestedAt);
        assertThat(result.response().user().therapistVerification().reviewedAt()).isNull();
        assertThat(result.response().user().therapistVerification().rejectionReason()).isNull();
    }

    @Test
    void 회원가입_성공_자동닉네임_자동로그인() {
        // given
        SignupRequest request = new SignupRequest(
                "test@test.com", "12345678",
                java.util.List.of(
                        new AgreementRequest("SERVICE_TERMS", "v1.0", true),
                        new AgreementRequest("PRIVACY_POLICY", "v1.0", true)
                )
        );

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("12345678")).thenReturn("encoded-password");
        when(nicknameGenerator.generate()).thenReturn("판다#1234");
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
        when(tokenService.createAccessToken(any(User.class))).thenReturn("access-token");
        when(tokenService.issueRefreshToken(any(User.class), any(UUID.class), eq("Mozilla"), eq("127.0.0.1")))
                .thenReturn(new TokenService.IssuedToken("raw-refresh", 1209600L));
        when(userAgreementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        AuthService.SignupResult result = authService.signup(request, "Mozilla", "127.0.0.1");

        // then
        assertThat(result.response().getId()).isEqualTo(1L);
        assertThat(result.response().getEmail()).isEqualTo("test@test.com");
        assertThat(result.response().getNickname()).isEqualTo("판다#1234");
        assertThat(result.response().getAccessToken()).isEqualTo("access-token");
        assertThat(result.response().getRole()).isEqualTo("USER");
        verify(nicknameGenerator).generate();
        verify(userAgreementRepository, times(2)).save(any());
    }

    @Test
    void 회원가입_실패_중복이메일() {
        // given
        SignupRequest request = new SignupRequest(
                "test@test.com", "12345678",
                java.util.List.of(
                        new AgreementRequest("SERVICE_TERMS", "v1.0", true),
                        new AgreementRequest("PRIVACY_POLICY", "v1.0", true)
                )
        );
        User existingUser = User.builder()
                .id(99L)
                .email("test@test.com")
                .passwordHash("encoded")
                .nickname("exists")
                .role(UserRole.USER)
                .build();
        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(existingUser));

        // when
        Throwable thrown = catchThrowable(() -> authService.signup(request, "Mozilla", "127.0.0.1"));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        assertThat(((CustomException) thrown).getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
        verify(userRepository, never()).save(any(User.class));
    }
}
