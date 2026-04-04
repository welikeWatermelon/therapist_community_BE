package com.therapyCommunity_Vol1.backend.therapist.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerification;
import com.therapyCommunity_Vol1.backend.therapist.dto.ApplyTherapistVerificationRequest;
import com.therapyCommunity_Vol1.backend.therapist.dto.TherapistVerificationResponse;
import com.therapyCommunity_Vol1.backend.therapist.repository.TherapistVerificationRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TherapistVerificationServiceTest {

    private TherapistVerificationRepository therapistVerificationRepository;
    private UserRepository userRepository;
    private FileStorageService fileStorageService;
    private TherapistVerificationService therapistVerificationService;

    @BeforeEach
    void setUp() {
        therapistVerificationRepository = mock(TherapistVerificationRepository.class);
        userRepository = mock(UserRepository.class);
        fileStorageService = mock(FileStorageService.class);
        therapistVerificationService = new TherapistVerificationService(
                therapistVerificationRepository,
                userRepository,
                fileStorageService
        );
    }

    @Test
    void 최초_치료사인증_신청_성공() {

        // given
        MockMultipartFile image = new MockMultipartFile(
                "licenseImage",
                "license.png",
                "image/png",
                "image".getBytes()
        );

        ApplyTherapistVerificationRequest request = new ApplyTherapistVerificationRequest();
        request.setLicenseCode("LICENSE-001");
        request.setLicenseImage(image);

        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .nickname("user")
                .role(UserRole.USER)
                .build();

        TherapistVerification saved = TherapistVerification.create(
                user,
                "LICENSE-001",
                "therapist-verifications/a.png",
                "license.png",
                "image/png"
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(therapistVerificationRepository.existsByLicenseCodeAndUserIdNot("LICENSE-001", 1L)).thenReturn(false);
        when(therapistVerificationRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(fileStorageService.storeTherapistVerificationImage(image))
                .thenReturn(new StoredFileInfo("therapist-verifications/a.png", "license.png", "image/png"));
        when(therapistVerificationRepository.save(any(TherapistVerification.class))).thenReturn(saved);

        // when
        TherapistVerificationResponse response = therapistVerificationService.apply(1L, request);

        // then
        assertThat(response.getLicenseCode()).isEqualTo("LICENSE-001");
        assertThat(response.getStatus().getCode()).isEqualTo("PENDING");
        assertThat(user.getRole()).isEqualTo(UserRole.THERAPIST);
        verify(therapistVerificationRepository).save(any(TherapistVerification.class));
    }

    @Test
    void 이미_치료사면_재신청_실패() {

        // given
        MockMultipartFile image = new MockMultipartFile(
                "licenseImage",
                "license.png",
                "image/png",
                "image".getBytes()
        );

        ApplyTherapistVerificationRequest request = new ApplyTherapistVerificationRequest();
        request.setLicenseCode("LICENSE-001");
        request.setLicenseImage(image);

        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .nickname("user")
                .role(UserRole.THERAPIST)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // when / then
        assertThatThrownBy(() -> therapistVerificationService.apply(1L, request))
                .isInstanceOf(CustomException.class);
        verify(fileStorageService, never()).storeTherapistVerificationImage(any());
        verify(therapistVerificationRepository, never()).save(any(TherapistVerification.class));
    }
}
