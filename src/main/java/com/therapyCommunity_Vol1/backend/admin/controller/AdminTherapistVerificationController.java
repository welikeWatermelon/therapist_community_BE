package com.therapyCommunity_Vol1.backend.admin.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.therapyCommunity_Vol1.backend.admin.dto.RejectTherapistVerificationRequest;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.admin.service.AdminTherapistVerificationService;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
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

@Tag(name = "관리자 — 치료사 인증", description = "치료사 인증 심사 (승인/거절)")
@RestController
@RequestMapping("/api/v1/admin/therapist-verifications")
@RequiredArgsConstructor
public class AdminTherapistVerificationController {

    private final AdminTherapistVerificationService adminTherapistVerificationService;

    @Operation(summary = "인증 신청 목록", description = "상태별 필터(PENDING/APPROVED/REJECTED), 페이징")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TherapistVerificationResponse>>> getVerifications(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        TherapistVerificationStatus parsedStatus = parseStatus(status);
        PagedResponse<TherapistVerificationResponse> response =
                adminTherapistVerificationService.getVerifications(parsedStatus, page, size);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "인증 승인", description = "PENDING → APPROVED. 유저는 이미 THERAPIST 상태")
    @PostMapping("/{verificationId}/approve")
    public ResponseEntity<ApiResponse<TherapistVerificationResponse>> approve(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long verificationId
    ) {
        TherapistVerificationResponse response =
                adminTherapistVerificationService.approve(
                        userDetails.getUserId(), verificationId
                );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "인증 거절", description = "PENDING → REJECTED + 유저 USER로 강등. 거절 사유 필수")
    @PostMapping("/{verificationId}/reject")
    public ResponseEntity<ApiResponse<TherapistVerificationResponse>> reject(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long verificationId,
            @Valid @RequestBody RejectTherapistVerificationRequest request
    ) {
        TherapistVerificationResponse response = adminTherapistVerificationService.reject(
                userDetails.getUserId(),
                verificationId,
                request
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "자격증 이미지 다운로드")
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
