package com.therapyCommunity_Vol1.backend.user.dto;

import com.therapyCommunity_Vol1.backend.therapist.dto.TherapistVerificationStatusDto;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;

import java.time.LocalDateTime;
import java.util.Optional;

public record CurrentUserResponse(
        Long id,
        String email,
        String nickname,
        String profileImageUrl,
        String role,
        boolean canAccessCommunity,
        String communityAccessLevel,
        TherapistVerificationSummary therapistVerification
) {

    public static CurrentUserResponse from(User user, Optional<TherapistVerificationStatusDto> verification) {
        String accessLevel = communityAccessLevel(user);
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getRole().getCode(),
                "FULL".equals(accessLevel),
                accessLevel,
                TherapistVerificationSummary.from(verification)
        );
    }

    private static String communityAccessLevel(User user) {
        return (user.getRole() == UserRole.THERAPIST || user.getRole() == UserRole.ADMIN)
                ? "FULL" : "PUBLIC_ONLY";
    }

    public record TherapistVerificationSummary(
            String status,
            LocalDateTime requestedAt,
            LocalDateTime reviewedAt,
            String rejectionReason
    ) {
        private static final String NOT_REQUESTED = "NOT_REQUESTED";

        public static TherapistVerificationSummary from(Optional<TherapistVerificationStatusDto> verification) {
            if (verification.isEmpty()) {
                return new TherapistVerificationSummary(NOT_REQUESTED, null, null, null);
            }

            TherapistVerificationStatusDto dto = verification.get();
            return new TherapistVerificationSummary(
                    dto.status(),
                    dto.requestedAt(),
                    dto.reviewedAt(),
                    dto.rejectionReason()
            );
        }
    }
}
