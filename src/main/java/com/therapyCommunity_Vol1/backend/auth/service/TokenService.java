package com.therapyCommunity_Vol1.backend.auth.service;

import com.therapyCommunity_Vol1.backend.auth.domain.RefreshToken;
import com.therapyCommunity_Vol1.backend.auth.dto.RefreshResponse;
import com.therapyCommunity_Vol1.backend.auth.repository.RefreshTokenRepository;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.JwtTokenProvider;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TokenService {

    private static final String REVOKE_REASON_REUSE_DETECTED = "REUSE_DETECTED";
    private static final String REVOKE_REASON_EXPIRED = "EXPIRED";
    private static final String REVOKE_REASON_ROTATED = "ROTATED";
    private static final String REVOKE_REASON_LOGOUT = "LOGOUT";
    private static final String REVOKE_REASON_WITHDRAW = "WITHDRAW";

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenManager refreshTokenManager;

    @Value("${jwt.refresh-ttl-sec}")
    private long refreshTokenTtlSec;

    public String createAccessToken(User user) {
        return jwtTokenProvider.createAccessToken(user.getId(), user.getRole().getCode());
    }

    public long getAccessTokenValiditySec() {
        return jwtTokenProvider.getAccessTokenValiditySec();
    }

    @Transactional
    public IssuedToken issueRefreshToken(User user, UUID tokenFamily, String userAgent, String ipAddress) {
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

        return new IssuedToken(rawToken, refreshTokenTtlSec);
    }

    @Transactional
    public RefreshResult refresh(String rawRefreshToken, String userAgent, String ipAddress) {
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
        if (user.isWithdrawn()) {
            currentRefreshToken.revoke(REVOKE_REASON_WITHDRAW);
            throw new CustomException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        currentRefreshToken.revoke(REVOKE_REASON_ROTATED);
        String accessToken = createAccessToken(user);
        long accessTokenExpiresInSec = jwtTokenProvider.getAccessTokenValiditySec();

        IssuedToken newRefreshToken =
                issueRefreshToken(user, currentRefreshToken.getTokenFamily(), userAgent, ipAddress);

        return new RefreshResult(
                new RefreshResponse(accessToken, accessTokenExpiresInSec),
                newRefreshToken.rawToken(),
                newRefreshToken.expiresInSec()
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

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId)
                .forEach(token -> token.revoke(REVOKE_REASON_WITHDRAW));
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

    public record IssuedToken(String rawToken, long expiresInSec) {}

    public record RefreshResult(
            RefreshResponse response,
            String refreshToken,
            long refreshTokenExpiresInSec
    ) {}
}
