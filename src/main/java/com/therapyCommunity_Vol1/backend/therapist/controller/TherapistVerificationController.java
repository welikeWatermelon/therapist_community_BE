package com.therapyCommunity_Vol1.backend.therapist.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.global.storage.StoredFileResource;
import com.therapyCommunity_Vol1.backend.therapist.dto.ApplyTherapistVerificationRequest;
import com.therapyCommunity_Vol1.backend.therapist.dto.TherapistVerificationResponse;
import com.therapyCommunity_Vol1.backend.therapist.service.TherapistVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "치료사 인증", description = "치료사 자격 인증 신청 및 조회")
@RestController
@RequestMapping("/api/v1/therapist-verifications")
@RequiredArgsConstructor
public class TherapistVerificationController {

    private final TherapistVerificationService therapistVerificationService;

    @Operation(summary = "치료사 인증 신청", description = "자격증 이미지 + 자격번호 제출. 신청 즉시 THERAPIST로 승격")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TherapistVerificationResponse>> apply(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute ApplyTherapistVerificationRequest request
    ) {
        TherapistVerificationResponse response =
                therapistVerificationService.apply(userDetails.getUser().getId(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @Operation(summary = "내 인증 현황 조회")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<TherapistVerificationResponse>> getMyVerification(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        TherapistVerificationResponse response =
                therapistVerificationService.getMyVerification(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "내 자격증 이미지 다운로드")
    @GetMapping("/me/image")
    public ResponseEntity<Resource> downloadMyVerificationImage(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        StoredFileResource storedFile =
                therapistVerificationService.loadMyVerificationImage(userDetails.getUser().getId());

        MediaType mediaType = MediaType.parseMediaType(storedFile.getContentType());

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename((storedFile.getOriginalFilename()))
                                .build()
                                .toString()
                )
                .body(storedFile.getResource());
    }
}
