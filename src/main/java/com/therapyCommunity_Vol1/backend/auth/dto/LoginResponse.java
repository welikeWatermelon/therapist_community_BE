package com.therapyCommunity_Vol1.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerification;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;

import java.time.LocalDateTime;
import java.util.Optional;

public record LoginResponse(
        @JsonProperty("isNewUser") boolean isNewUser,
        UserSummary user,
        Tokens tokens
) {

    public static LoginResponse of(
            boolean isNewUser,
            User user,
            Optional<TherapistVerification> verification,
            String accessToken,
            long accessTokenExpiresInSec
    ) {
        return new LoginResponse(
                isNewUser,
                UserSummary.from(user, verification),
                new Tokens(accessToken, accessTokenExpiresInSec)
        );
    }

    public record UserSummary(
            Long id,
            String email,
            String nickname,
            String profileImageUrl,
            String role,
            boolean canAccessCommunity,
            TherapistVerificationSummary therapistVerification
    ) {
        public static UserSummary from(User user, Optional<TherapistVerification> verification) {
            return new UserSummary(
                    user.getId(),
                    user.getEmail(),
                    user.getNickname(),
                    user.getProfileImageUrl(),
                    user.getRole().getCode(),
                    canAccessCommunity(user),
                    TherapistVerificationSummary.from(verification)
            );
        }

        private static boolean canAccessCommunity(User user) {
            return user.getRole() == UserRole.THERAPIST || user.getRole() == UserRole.ADMIN;
        }
    }

    public record TherapistVerificationSummary(
            String status,
            LocalDateTime requestedAt,
            LocalDateTime reviewedAt,
            String rejectionReason
    ) {
        private static final String NOT_REQUESTED = "NOT_REQUESTED";

        public static TherapistVerificationSummary from(Optional<TherapistVerification> verification) {
            if (verification.isEmpty()) {
                return new TherapistVerificationSummary(NOT_REQUESTED, null, null, null);
            }

            TherapistVerification therapistVerification = verification.get();
            return new TherapistVerificationSummary(
                    therapistVerification.getStatus().getCode(),
                    therapistVerification.getCreatedAt(),
                    therapistVerification.getReviewedAt(),
                    therapistVerification.getRejectReason()
            );
        }
    }

    public record Tokens(
            String accessToken,
            long accessTokenExpiresInSec
    ) {}
}
