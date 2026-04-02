package com.therapyCommunity_Vol1.backend.therapist.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.storage.FileStorageService;
import com.therapyCommunity_Vol1.backend.global.storage.StoredFileInfo;
import com.therapyCommunity_Vol1.backend.global.storage.StoredFileResource;
import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerification;
import com.therapyCommunity_Vol1.backend.therapist.dto.ApplyTherapistVerificationRequest;
import com.therapyCommunity_Vol1.backend.therapist.dto.TherapistVerificationResponse;
import com.therapyCommunity_Vol1.backend.therapist.repository.TherapistVerificationRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TherapistVerificationService {
    private static final String EVT_FILE_DELETE_FAILED = "FILE_DELETE_FAILED";
    private static final String EVT_APPLY_FAILED_AFTER_UPLOAD = "THERAPIST_APPLY_FAILED_AFTER_UPLOAD";

    private final TherapistVerificationRepository therapistVerificationRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public TherapistVerificationResponse apply(
            Long currentUserId,
            ApplyTherapistVerificationRequest request
    ) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        // 신청 전 사전 검증: 권한, 파일, 자격증 코드 중복을 먼저 차단한다.
        validateUserCanApply(user);
        validateImage(request.getLicenseImage());
        validateLicenseCodeAvailable(request.getLicenseCode(), currentUserId);
        // 기존 신청이 있으면 상태를 확인해 재신청 가능 여부를 판단한다.
        // (예: PENDING이면 재신청 불가)
        Optional<TherapistVerification> existingVerification =
                therapistVerificationRepository.findByUserId(currentUserId);
        existingVerification.ifPresent(this::validateCanReapply);

        String oldStoredPath = existingVerification
                .map(TherapistVerification::getLicenseImagePath)
                .orElse(null);

        StoredFileInfo storedFileInfo =
                fileStorageService.storeTherapistVerificationImage(request.getLicenseImage());
        try {
            TherapistVerification verification = existingVerification
                    .map(existing -> reapply(existing, request, storedFileInfo))
                    .orElseGet(() -> createNew(user, request, storedFileInfo));

            if (oldStoredPath != null
                    && !oldStoredPath.isBlank()
                    && !oldStoredPath.equals(storedFileInfo.getStoredPath())) {
                scheduleDeleteAfterCommit(oldStoredPath, currentUserId);
            }

            return TherapistVerificationResponse.from(
                    verification,
                    "/api/v1/therapist-verifications/me/image"
            );
        } catch (RuntimeException e) {
            log.error(
                    "{} userId={} licenseCode={} uploadedPath={}",
                    EVT_APPLY_FAILED_AFTER_UPLOAD,
                    currentUserId,
                    request.getLicenseCode(),
                    storedFileInfo.getStoredPath(),
                    e
            );
            safeDelete(storedFileInfo.getStoredPath(), "rollback_cleanup_after_db_failure", currentUserId); //DB 실패 시 새 파일 orphan 방지
            throw e;
        }
    }

    private void validateLicenseCodeAvailable(String licenseCode, Long currentUserId) {
        if (therapistVerificationRepository.existsByLicenseCodeAndUserIdNot(licenseCode, currentUserId)) {
            throw new CustomException(ErrorCode.LICENSE_CODE_ALREADY_USED);
        }
    }

    private void scheduleDeleteAfterCommit(String oldStoredPath, Long currentUserId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeDelete(oldStoredPath, "replace_old_file_after_commit", currentUserId);
                }
            });
        } else {
            safeDelete(oldStoredPath, "replace_old_file_no_tx", currentUserId);
        }
    }

    private void safeDelete(String storedPath, String reason, Long userId) {
        try {
            fileStorageService.delete(storedPath);
        } catch (Exception e) {
            log.warn(
                    "{} reason={} userId={} storedPath={}",
                    EVT_FILE_DELETE_FAILED,
                    reason,
                    userId,
                    storedPath,
                    e
            );
        }
    }

    public Optional<TherapistVerification> findByUserId(Long userId) {
        return therapistVerificationRepository.findByUserId(userId);
    }

    public TherapistVerificationResponse getMyVerification(Long currentUserId) {
        return therapistVerificationRepository.findByUserId(currentUserId)
                .map(verification -> TherapistVerificationResponse.from(
                        verification,
                        "/api/v1/therapist-verifications/me/image"
                ))
                .orElse(null);
    }

    public StoredFileResource loadMyVerificationImage(Long currentUserId) {
        TherapistVerification verification = therapistVerificationRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.THERAPIST_VERIFICATION_NOT_FOUND));

        return fileStorageService.loadAsResource(
                verification.getLicenseImagePath(),
                verification.getLicenseImageContentType(),
                verification.getLicenseImageOriginalName()
        );
    }

    private TherapistVerification createNew(
            User user,
            ApplyTherapistVerificationRequest request,
            StoredFileInfo storedFileInfo
    ) {
        TherapistVerification verification = TherapistVerification.create(
                user,
                request.getLicenseCode(),
                storedFileInfo.getStoredPath(),
                storedFileInfo.getOriginalFilename(),
                storedFileInfo.getContentType()
        );

        return therapistVerificationRepository.save(verification);
    }

    private TherapistVerification reapply(
            TherapistVerification existing,
            ApplyTherapistVerificationRequest request,
            StoredFileInfo storedFileInfo
    ) {
        existing.reapply(
                request.getLicenseCode(),
                storedFileInfo.getStoredPath(),
                storedFileInfo.getOriginalFilename(),
                storedFileInfo.getContentType()
        );
        return existing;
    }

    private void validateCanReapply(TherapistVerification existing) {
        if (existing.isPending()) {
            throw new CustomException(ErrorCode.THERAPIST_VERIFICATION_ALREADY_PENDING);
        }

        if (existing.isApproved()) {
            throw new CustomException(ErrorCode.THERAPIST_ALREADY_VERIFIED);
        }
    }

    private void validateUserCanApply(User user) {
        if (user.getRole() == UserRole.THERAPIST || user.getRole() == UserRole.ADMIN) {
            throw new CustomException(ErrorCode.THERAPIST_ALREADY_VERIFIED);
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_LICENSE_IMAGE);
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CustomException(ErrorCode.INVALID_LICENSE_IMAGE);
        }
    }
}
