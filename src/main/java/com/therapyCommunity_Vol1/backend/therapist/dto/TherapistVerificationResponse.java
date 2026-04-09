package com.therapyCommunity_Vol1.backend.therapist.dto;

import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerification;
import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class TherapistVerificationResponse {

    private Long id;
    private Long userId;
    private String userEmail;
    private String userNickname;
    private String licenseCode;
    private String licenseImageOriginName;
    private String licenseImageDownloadUrl;
    private TherapistVerificationStatus status;
    private Long reviewedById;
    private String reviewedByNickname;
    private LocalDateTime reviewedAt;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean demoted;

    public static TherapistVerificationResponse from(
            TherapistVerification verification,
            String licenseImageDownloadUrl
    ) {
        return from(verification, licenseImageDownloadUrl, false);
    }

    public static TherapistVerificationResponse from(
            TherapistVerification verification,
            String licenseImageDownloadUrl,
            boolean demoted
    ) {
        return new TherapistVerificationResponse(
                verification.getId(),
                verification.getUser().getId(),
                verification.getUser().getEmail(),
                verification.getUser().getNickname(),
                verification.getLicenseCode(),
                verification.getLicenseImageOriginalName(),
                licenseImageDownloadUrl,
                verification.getStatus(),
                verification.getReviewedBy() != null ? verification.getReviewedBy().getId() : null,
                verification.getReviewedBy() != null ? verification.getReviewedBy().getNickname() : null,
                verification.getReviewedAt(),
                verification.getRejectReason(),
                verification.getCreatedAt(),
                verification.getUpdatedAt(),
                demoted
        );
    }

}
