package com.therapyCommunity_Vol1.backend.auth.service;

import com.therapyCommunity_Vol1.backend.auth.domain.AgreementType;
import com.therapyCommunity_Vol1.backend.auth.domain.RefreshToken;
import com.therapyCommunity_Vol1.backend.auth.domain.UserAgreement;
import com.therapyCommunity_Vol1.backend.auth.dto.AgreementRequest;
import com.therapyCommunity_Vol1.backend.auth.dto.LoginRequest;
import com.therapyCommunity_Vol1.backend.auth.dto.LoginResponse;
import com.therapyCommunity_Vol1.backend.auth.dto.RefreshResponse;
import com.therapyCommunity_Vol1.backend.auth.dto.SignupRequest;
import com.therapyCommunity_Vol1.backend.auth.dto.SignupResponse;
import com.therapyCommunity_Vol1.backend.auth.repository.RefreshTokenRepository;
import com.therapyCommunity_Vol1.backend.auth.repository.UserAgreementRepository;
import com.therapyCommunity_Vol1.backend.auth.support.NicknameGenerator;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.JwtTokenProvider;
import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerification;
import com.therapyCommunity_Vol1.backend.therapist.service.TherapistVerificationService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String REVOKE_REASON_REUSE_DETECTED = "REUSE_DETECTED";
    private static final String REVOKE_REASON_EXPIRED = "EXPIRED";
    private static final String REVOKE_REASON_ROTATED = "ROTATED";
    private static final String REVOKE_REASON_LOGOUT = "LOGOUT";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenManager refreshTokenManager;
    private final TherapistVerificationService therapistVerificationService;
    private final NicknameGenerator nicknameGenerator;
    private final UserAgreementRepository userAgreementRepository;

    @Value("${jwt.refresh-ttl-sec}")
    private long refreshTokenTtlSec;

    @Transactional
    public SignupResult signup(SignupRequest request, String userAgent, String ipAddress) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new CustomException(ErrorCode.CONFLICT);
        }

        validateRequiredAgreements(request.getAgreements());

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        String nickname = nicknameGenerator.generate();

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(encodedPassword)
                .nickname(nickname)
                .role(UserRole.USER)
                .build();

        User savedUser = userRepository.save(user);
        saveAgreements(savedUser, request.getAgreements());

        String accessToken = createAccessToken(savedUser);
        IssuedRefreshToken issuedRefreshToken =
                issueRefreshToken(savedUser, UUID.randomUUID(), userAgent, ipAddress);

        return new SignupResult(
                new SignupResponse(
                        savedUser.getId(),
                        savedUser.getEmail(),
                        savedUser.getNickname(),
                        accessToken,
                        savedUser.getRole().getCode()
                ),
                issuedRefreshToken.rawToken(),
                refreshTokenTtlSec
        );
    }

    private void validateRequiredAgreements(java.util.List<AgreementRequest> agreements) {
        Map<String, Boolean> agreedMap = agreements.stream()
                .collect(Collectors.toMap(AgreementRequest::getType, AgreementRequest::isAgreed));

        Arrays.stream(AgreementType.values())
                .filter(AgreementType::isRequired)
                .forEach(requiredType -> {
                    Boolean agreed = agreedMap.get(requiredType.name());
                    if (agreed == null || !agreed) {
                        throw new CustomException(ErrorCode.INVALID_INPUT);
                    }
                });
    }

    private void saveAgreements(User user, java.util.List<AgreementRequest> agreements) {
        agreements.stream()
                .filter(AgreementRequest::isAgreed)
                .forEach(req -> {
                    AgreementType type = AgreementType.valueOf(req.getType());
                    userAgreementRepository.save(UserAgreement.create(user, type, req.getVersion()));
                });
    }

    public record SignupResult(
            SignupResponse response,
            String refreshToken,
            long refreshTokenExpiresInSec
    ) {}

    @Transactional
    public LoginResult login(
            LoginRequest request,
            String userAgent,
            String ipAddress
    ) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        String accessToken = createAccessToken(user);
        long accessTokenExpiresInSec = jwtTokenProvider.getAccessTokenValiditySec();

        IssuedRefreshToken issuedRefreshToken =
                issueRefreshToken(user, UUID.randomUUID(), userAgent, ipAddress);
        Optional<TherapistVerification> verification =
                therapistVerificationService.findByUserId(user.getId());

        return new LoginResult(
                LoginResponse.of(
                        user,
                        verification,
                        accessToken,
                        accessTokenExpiresInSec
                ),
                issuedRefreshToken.rawToken(),
                refreshTokenTtlSec
        );
    }

    @Transactional
    public RefreshResult refresh(
            String rawRefreshToken,
            String userAgent,
            String ipAddress
    ) {
        RefreshToken currentRefreshToken = findRefreshTokenByRawTokenOrThrow(rawRefreshToken);

        if (currentRefreshToken.isRevoked()) {
            revokeFamily(currentRefreshToken.getTokenFamily(), REVOKE_REASON_REUSE_DETECTED);
            throw new CustomException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        if (currentRefreshToken.isExpired()) {
            currentRefreshToken.revoke(REVOKE_REASON_EXPIRED);
            throw new CustomException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        User user = currentRefreshToken.getUser();
        currentRefreshToken.revoke(REVOKE_REASON_ROTATED);
        String accessToken = createAccessToken(user);
        long accessTokenExpiresInSec = jwtTokenProvider.getAccessTokenValiditySec();

        IssuedRefreshToken newRefreshToken =
                issueRefreshToken(user, currentRefreshToken.getTokenFamily(), userAgent, ipAddress);

        return new RefreshResult(
                new RefreshResponse(accessToken, accessTokenExpiresInSec),
                newRefreshToken.rawToken(),
                refreshTokenTtlSec
        );
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(refreshTokenManager.hash(rawRefreshToken))
                .ifPresent(refreshToken -> {
                    if (!refreshToken.isRevoked()) {
                        refreshToken.revoke(REVOKE_REASON_LOGOUT);
                    }
                });
    }

    private IssuedRefreshToken issueRefreshToken(
            User user,
            UUID tokenFamily,
            String userAgent,
            String ipAddress
    ) {
        String rawToken = refreshTokenManager.generateRawToken();
        String tokenHash = refreshTokenManager.hash(rawToken);

        RefreshToken refreshToken = RefreshToken.issue(
                user,
                tokenHash,
                tokenFamily,
                userAgent,
                ipAddress,
                LocalDateTime.now().plusSeconds(refreshTokenTtlSec)
        );

        refreshTokenRepository.save(refreshToken);

        return new IssuedRefreshToken(rawToken);
    }

    private User findUserByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private void validatePasswordOrThrow(String rawPassword, String passwordHash) {
        if (!passwordEncoder.matches(rawPassword, passwordHash)) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }
    }

    private String createAccessToken(User user) {
        return jwtTokenProvider.createAccessToken(user.getId(), user.getRole().getCode());
    }

    private RefreshToken findRefreshTokenByRawTokenOrThrow(String rawRefreshToken) {
        String tokenHash = refreshTokenManager.hash(rawRefreshToken);
        return refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_INVALID));
    }

    private void revokeFamily(UUID tokenFamily, String revokeReason) {
        refreshTokenRepository.findByTokenFamilyAndRevokedAtIsNull(tokenFamily)
                .forEach(token -> token.revoke(revokeReason));
    }

    public record LoginResult(
            LoginResponse response,
            String refreshToken,
            long refreshTokenExpiresInSec
    ) {}

    public record RefreshResult(
            RefreshResponse response,
            String refreshToken,
            long refreshTokenExpiresInSec
    ) {}

    private record IssuedRefreshToken(String rawToken) {}
}
