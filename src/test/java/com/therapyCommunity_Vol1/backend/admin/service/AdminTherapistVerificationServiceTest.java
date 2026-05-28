package com.therapyCommunity_Vol1.backend.admin.service;

import com.therapyCommunity_Vol1.backend.admin.dto.RejectTherapistVerificationRequest;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerificationStatus;
import com.therapyCommunity_Vol1.backend.therapist.dto.TherapistVerificationResponse;
import com.therapyCommunity_Vol1.backend.therapist.service.TherapistVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminTherapistVerificationServiceTest {

    private FileStorageService fileStorageService;
    private TherapistVerificationService therapistVerificationService;
    private AdminTherapistVerificationService adminTherapistVerificationService;

    @BeforeEach
    void setUp() {
        fileStorageService = mock(FileStorageService.class);
        therapistVerificationService = mock(TherapistVerificationService.class);
        adminTherapistVerificationService = new AdminTherapistVerificationService(
                fileStorageService,
                therapistVerificationService
        );
    }

    @Test
    void 관리자_승인시_상태가_APPROVED로_변경된다() {

        // given
        Long adminUserId = 2L;
        Long verificationId = 10L;

        TherapistVerificationResponse expectedResponse = new TherapistVerificationResponse(
                verificationId, 1L, "user@test.com", "user",
                "LICENSE-001", "license.png",
                "/api/v1/admin/therapist-verifications/10/image",
                TherapistVerificationStatus.APPROVED,
                adminUserId, "admin",
                LocalDateTime.now(), null,
                LocalDateTime.now(), LocalDateTime.now(),
                false
        );

        when(therapistVerificationService.approveVerificationReview(verificationId, adminUserId))
                .thenReturn(expectedResponse);

        // when
        TherapistVerificationResponse response =
                adminTherapistVerificationService.approve(adminUserId, verificationId);

        // then
        assertThat(response.getStatus().getCode()).isEqualTo("APPROVED");
        verify(therapistVerificationService).approveVerificationReview(verificationId, adminUserId);
    }

    @Test
    void 관리자_거절시_rejectReason_저장되고_사용자가_USER로_강등된다() {

        // given
        Long adminUserId = 2L;
        Long verificationId = 10L;
        String reason = "번호가 불명확합니다.";

        TherapistVerificationResponse expectedResponse = new TherapistVerificationResponse(
                verificationId, 1L, "user@test.com", "user",
                "LICENSE-001", "license.png",
                "/api/v1/admin/therapist-verifications/10/image",
                TherapistVerificationStatus.REJECTED,
                adminUserId, "admin",
                LocalDateTime.now(), reason,
                LocalDateTime.now(), LocalDateTime.now(),
                true
        );

        when(therapistVerificationService.rejectVerificationReview(verificationId, adminUserId, reason))
                .thenReturn(expectedResponse);

        // when
        TherapistVerificationResponse response =
                adminTherapistVerificationService.reject(
                        adminUserId,
                        verificationId,
                        new RejectTherapistVerificationRequest(reason)
                );

        // then
        assertThat(response.getStatus().getCode()).isEqualTo("REJECTED");
        assertThat(response.getRejectReason()).isEqualTo("번호가 불명확합니다.");
        assertThat(response.isDemoted()).isTrue();
        verify(therapistVerificationService).rejectVerificationReview(verificationId, adminUserId, reason);
    }
}
