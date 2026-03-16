package com.therapyCommunity_Vol1.backend.therapist.domain;

import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "therapist_verifications",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_therapist_verifications_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_therapist_verifications_license_code", columnNames = "license_code")
        }
)
@NoArgsConstructor
public class TherapistVerification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "license_code", nullable = false, length = 100)
    private String licenseCode;

    @Column(name = "license_image_path", nullable = false)
    private String licenseImagePath;

    @Column(name = "license_image_original_name",nullable = false, length = 255)
    private String licenseImageOriginalName;

    @Column(name = "license_image_content_type", nullable = false, length = 100)
    private String licenseImageContentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TherapistVerificationStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    private TherapistVerification(
            User user,
            String licenseCode,
            String licenseImagePath,
            String licenseImageOriginalName,
            String licenseImageContentType
    ) {
        this.user = user;
        this.licenseCode = licenseCode;
        this.licenseImagePath = licenseImagePath;
        this.licenseImageOriginalName = licenseImageOriginalName;
        this.licenseImageContentType = licenseImageContentType;
        this.status = TherapistVerificationStatus.PENDING;
    }

    public static TherapistVerification create(
            User user,
            String licenseCode,
            String licenseImagePath,
            String licenseImageOriginalName,
            String licenseImageContentType
    ) {
        return new TherapistVerification(
                user,
                licenseCode,
                licenseImagePath,
                licenseImageOriginalName,
                licenseImageContentType
        );
    }

    public void reapply(
            String licenseCode,
            String licenseImagePath,
            String licenseImageOriginalName,
            String licenseImageContentType
    ) {
        this.licenseCode =licenseCode;
        this.licenseImagePath = licenseImagePath;
        this.licenseImageOriginalName = licenseImageOriginalName;
        this.licenseImageContentType = licenseImageContentType;
        this.status = TherapistVerificationStatus.PENDING;
        this.reviewedBy = null;
        this.reviewedAt = null;
        this.rejectReason = null;
    }

    public void approve(User admin) {
        this.status = TherapistVerificationStatus.APPROVED;
        this.reviewedBy = admin;
        this.reviewedAt = LocalDateTime.now();
        this.rejectReason = null;
    }

    public void reject(User admin, String rejectReason) {
        this.status = TherapistVerificationStatus.REJECTED;
        this.reviewedBy = admin;
        this.reviewedAt =LocalDateTime.now();
        this.rejectReason = rejectReason;
    }

    public boolean isPending() {
        return this.status == TherapistVerificationStatus.PENDING;
    }

    public boolean isApproved() {
        return this.status == TherapistVerificationStatus.APPROVED;
    }

    public boolean isRejected() {
        return this.status == TherapistVerificationStatus.REJECTED;
    }


}
