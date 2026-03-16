package com.therapyCommunity_Vol1.backend.therapist.controller;

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

@RestController
@RequestMapping("/api/v1/therapist-verifications")
@RequiredArgsConstructor
public class TherapistVerificationController {

    private final TherapistVerificationService therapistVerificationService;

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

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<TherapistVerificationResponse>> getMyVerification(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        TherapistVerificationResponse response =
                therapistVerificationService.getMyVerification(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

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
