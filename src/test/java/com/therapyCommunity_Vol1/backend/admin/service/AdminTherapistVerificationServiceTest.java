package com.therapyCommunity_Vol1.backend.admin.service;

import com.therapyCommunity_Vol1.backend.admin.dto.RejectTherapistVerificationRequest;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerification;
import com.therapyCommunity_Vol1.backend.therapist.dto.TherapistVerificationResponse;
import com.therapyCommunity_Vol1.backend.therapist.repository.TherapistVerificationRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminTherapistVerificationServiceTest {

    private TherapistVerificationRepository therapistVerificationRepository;
    private UserRepository userRepository;
    private FileStorageService fileStorageService;
    private AdminTherapistVerificationService adminTherapistVerificationService;

    @BeforeEach
    void setUp() {
        therapistVerificationRepository = mock(TherapistVerificationRepository.class);
        userRepository = mock(UserRepository.class);
        fileStorageService = mock(FileStorageService.class);
        adminTherapistVerificationService = new AdminTherapistVerificationService(
                therapistVerificationRepository,
                userRepository,
                fileStorageService
        );
    }

    @Test
    void 관리자_승인시_상태가_APPROVED로_변경된다() {

        // given
        User applicant = User.builder()
                .id(1L)
                .email("user@test.com")
                .nickname("user")
                .role(UserRole.THERAPIST)
                .build();

        User admin = User.builder()
                .id(2L)
                .email("admin@test.com")
                .nickname("admin")
                .role(UserRole.ADMIN)
                .build();

        TherapistVerification verification = TherapistVerification.create(
                applicant,
                "LICENSE-001",
                "therapist-verifications/a.png",
                "license.png",
                "image/png"
        );

        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(therapistVerificationRepository.findWithUserById(10L)).thenReturn(Optional.of(verification));

        // when
        TherapistVerificationResponse response =
                adminTherapistVerificationService.approve(2L, 10L);

        // then
        assertThat(response.getStatus().getCode()).isEqualTo("APPROVED");
        assertThat(applicant.getRole()).isEqualTo(UserRole.THERAPIST);
    }

    @Test
    void 관리자_거절시_rejectReason_저장되고_사용자가_USER로_강등된다() {

        // given
        User applicant = User.builder()
                .id(1L)
                .email("user@test.com")
                .nickname("user")
                .role(UserRole.THERAPIST)
                .build();

        User admin = User.builder()
                .id(2L)
                .email("admin@test.com")
                .nickname("admin")
                .role(UserRole.ADMIN)
                .build();

        TherapistVerification verification = TherapistVerification.create(
                applicant,
                "LICENSE-001",
                "therapist-verifications/a.png",
                "license.png",
                "image/png"
        );

        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(therapistVerificationRepository.findWithUserById(10L)).thenReturn(Optional.of(verification));

        // when
        TherapistVerificationResponse response =
                adminTherapistVerificationService.reject(
                        2L,
                        10L,
                        new RejectTherapistVerificationRequest("번호가 불명확합니다.")
                );

        // then
        assertThat(response.getStatus().getCode()).isEqualTo("REJECTED");
        assertThat(response.getRejectReason()).isEqualTo("번호가 불명확합니다.");
        assertThat(response.isDemoted()).isTrue();
        assertThat(applicant.getRole()).isEqualTo(UserRole.USER);
    }
}