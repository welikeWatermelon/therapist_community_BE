package com.therapyCommunity_Vol1.backend.auth.service;

import com.therapyCommunity_Vol1.backend.auth.domain.AgreementType;
import com.therapyCommunity_Vol1.backend.auth.domain.UserAgreement;
import com.therapyCommunity_Vol1.backend.auth.dto.AgreementRequest;
import com.therapyCommunity_Vol1.backend.auth.dto.LoginRequest;
import com.therapyCommunity_Vol1.backend.auth.dto.LoginResponse;
import com.therapyCommunity_Vol1.backend.auth.dto.SignupRequest;
import com.therapyCommunity_Vol1.backend.auth.dto.SignupResponse;
import com.therapyCommunity_Vol1.backend.auth.repository.UserAgreementRepository;
import com.therapyCommunity_Vol1.backend.auth.support.NicknameGenerator;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerification;
import com.therapyCommunity_Vol1.backend.therapist.service.TherapistVerificationService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final TherapistVerificationService therapistVerificationService;
    private final NicknameGenerator nicknameGenerator;
    private final UserAgreementRepository userAgreementRepository;

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

        String accessToken = tokenService.createAccessToken(savedUser);
        TokenService.IssuedToken issuedRefreshToken =
                tokenService.issueRefreshToken(savedUser, UUID.randomUUID(), userAgent, ipAddress);

        return new SignupResult(
                new SignupResponse(
                        savedUser.getId(),
                        savedUser.getEmail(),
                        savedUser.getNickname(),
                        accessToken,
                        savedUser.getRole().getCode()
                ),
                issuedRefreshToken.rawToken(),
                issuedRefreshToken.expiresInSec()
        );
    }

    @Transactional
    public LoginResult login(
            LoginRequest request,
            String userAgent,
            String ipAddress
    ) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));
        if (user.isWithdrawn()) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = tokenService.createAccessToken(user);
        long accessTokenExpiresInSec = tokenService.getAccessTokenValiditySec();

        TokenService.IssuedToken issuedRefreshToken =
                tokenService.issueRefreshToken(user, UUID.randomUUID(), userAgent, ipAddress);

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
                issuedRefreshToken.expiresInSec()
        );
    }

    private void validateRequiredAgreements(java.util.List<AgreementRequest> agreements) {
        Map<String, Boolean> agreedMap = agreements.stream()
                .collect(Collectors.toMap(AgreementRequest::getType, AgreementRequest::isAgreed, (a, b) -> b));

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

    public record LoginResult(
            LoginResponse response,
            String refreshToken,
            long refreshTokenExpiresInSec
    ) {}
}
