package com.therapyCommunity_Vol1.backend.admin.service;

import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.file.service.FileStorageService;
import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerification;
import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerificationStatus;
import com.therapyCommunity_Vol1.backend.admin.dto.RejectTherapistVerificationRequest;
import com.therapyCommunity_Vol1.backend.therapist.dto.TherapistVerificationResponse;
import com.therapyCommunity_Vol1.backend.therapist.repository.TherapistVerificationRepository;
import com.therapyCommunity_Vol1.backend.therapist.service.TherapistVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTherapistVerificationService {

    private final TherapistVerificationRepository therapistVerificationRepository;
    private final FileStorageService fileStorageService;
    private final TherapistVerificationService therapistVerificationService;

    public PagedResponse<TherapistVerificationResponse> getVerifications(
            TherapistVerificationStatus status,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        Page<TherapistVerification> result = (status == null)
                ? therapistVerificationRepository.findAll(pageable)
                : therapistVerificationRepository.findByStatus(status, pageable);
        List<TherapistVerificationResponse> responses = result.getContent()
                .stream()
                .map(v -> TherapistVerificationResponse.from(
                        v,
                        "/api/v1/admin/therapist-verifications/" + v.getId() + "/image"
                ))
                .toList();
        return PagedResponse.from(result, responses);
    }

    @Transactional
    public TherapistVerificationResponse approve(Long adminUserId, Long verificationId) {
        return therapistVerificationService.approveVerificationReview(verificationId, adminUserId);
    }

    @Transactional
    public TherapistVerificationResponse reject(
            Long adminUserId,
            Long verificationId,
            RejectTherapistVerificationRequest request
    ) {
        return therapistVerificationService.rejectVerificationReview(verificationId, adminUserId, request.getRejectReason());
    }

    public StoredFileResource loadVerificationImage(Long verificationId) {
        TherapistVerification verification = therapistVerificationRepository.findWithUserById(verificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.THERAPIST_VERIFICATION_NOT_FOUND));

        return fileStorageService.loadAsResource(
                verification.getLicenseImagePath(),
                verification.getLicenseImageContentType(),
                verification.getLicenseImageOriginalName()
        );
    }

}
