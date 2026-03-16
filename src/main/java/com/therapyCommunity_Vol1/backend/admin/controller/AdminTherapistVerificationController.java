package com.therapyCommunity_Vol1.backend.admin.controller;


import com.therapyCommunity_Vol1.backend.admin.dto.RejectTherapistVerificationRequest;
import com.therapyCommunity_Vol1.backend.admin.dto.TherapistVerificationPageResponse;
import com.therapyCommunity_Vol1.backend.admin.service.AdminTherapistVerificationService;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.global.storage.StoredFileResource;
import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerificationStatus;
import com.therapyCommunity_Vol1.backend.therapist.dto.TherapistVerificationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/therapist-verifications")
@RequiredArgsConstructor
public class AdminTherapistVerificationController {

    private final AdminTherapistVerificationService adminTherapistVerificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<TherapistVerificationPageResponse>> getVerifications(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        TherapistVerificationStatus parsedStatus = parseStatus(status);
        TherapistVerificationPageResponse response =
                adminTherapistVerificationService.getVerifications(parsedStatus, page, size);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{verificationId}/approve")
    public ResponseEntity<ApiResponse<TherapistVerificationResponse>> approve(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long verificationId
    ) {
        TherapistVerificationResponse response =
                adminTherapistVerificationService.approve(
                        userDetails.getUser().getId(), verificationId
                );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{verificationId}/reject")
    public ResponseEntity<ApiResponse<TherapistVerificationResponse>> reject(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long verificationId,
            @Valid @RequestBody RejectTherapistVerificationRequest request
    ) {
        TherapistVerificationResponse response = adminTherapistVerificationService.reject(
                userDetails.getUser().getId(),
                verificationId,
                request
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{verificationId}/image")
    public ResponseEntity<Resource> downloadVerificationImage(
            @PathVariable Long verificationId
    ) {
        StoredFileResource storedFile =
                adminTherapistVerificationService.loadVerificationImage(verificationId);

        MediaType mediaType = MediaType.parseMediaType(storedFile.getContentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(storedFile.getOriginalFilename())
                                .build()
                                .toString()
                )
                .body(storedFile.getResource());
    }

    private TherapistVerificationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TherapistVerificationStatus.fromCode(status);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

}
