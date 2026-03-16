package com.therapyCommunity_Vol1.backend.therapist.domain;

import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TherapistVerificationTest {

    @Test
    void 최초_신청을_생성할_수_있다() {

        // given
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .nickname("user")
                .role(UserRole.USER)
                .build();

        // when
        TherapistVerification verification = TherapistVerification.create(
                user,
                "LICENSE-001",
                "therapist-verifications/a.png",
                "license.png",
                "image/png"
        );

        // then
        assertThat(verification.getStatus()).isEqualTo(TherapistVerificationStatus.PENDING);
        assertThat(verification.getLicenseCode()).isEqualTo("LICENSE-001");
    }

    @Test
    void 승인할_수_있다() {

        // given
        User user = User.builder().id(1L).email("user@test.com").nickname("user").role(UserRole.USER).build();
        User admin = User.builder().id(2L).email("admin@test.com").nickname("admin").role(UserRole.ADMIN).build();

        TherapistVerification verification = TherapistVerification.create(
                user,
                "LICENSE-001",
                "therapist-verifications/a.png",
                "license.png",
                "image/png"
        );

        // when
        verification.approve(admin);

        // then
        assertThat(verification.getStatus()).isEqualTo(TherapistVerificationStatus.APPROVED);
        assertThat(verification.getReviewedBy()).isEqualTo(admin);
    }

    @Test
    void 거절후_재신청할_수_있다() {

        // given
        User user = User.builder().id(1L).email("user@test.com").nickname("user").role(UserRole.USER).build();
        User admin = User.builder().id(2L).email("admin@test.com").nickname("admin").role(UserRole.ADMIN).build();

        TherapistVerification verification = TherapistVerification.create(
                user,
                "LICENSE-001",
                "therapist-verifications/a.png",
                "license.png",
                "image/png"
        );
        verification.reject(admin, "사유");

        // when
        verification.reapply(
                "LICENSE-002",
                "therapist-verifications/b.png",
                "license2.png",
                "image/png"
        );

        // then
        assertThat(verification.getStatus()).isEqualTo(TherapistVerificationStatus.PENDING);
        assertThat(verification.getRejectReason()).isNull();
        assertThat(verification.getLicenseCode()).isEqualTo("LICENSE-002");
    }
}