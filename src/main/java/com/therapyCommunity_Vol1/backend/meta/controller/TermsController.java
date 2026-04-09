package com.therapyCommunity_Vol1.backend.meta.controller;

import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.meta.service.TermsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "약관", description = "이용약관, 개인정보처리방침 조회")
@RestController
@RequestMapping("/api/v1/terms")
@RequiredArgsConstructor
public class TermsController {

    private final TermsService termsService;

    @Operation(summary = "이용약관 URL", description = "S3 presigned URL (10분 유효)", security = {})
    @GetMapping("/service")
    public ResponseEntity<ApiResponse<Map<String, String>>> getTermsUrl() {
        String url = termsService.getTermsUrl();
        return ResponseEntity.ok(ApiResponse.success(Map.of("url", url)));
    }

    @Operation(summary = "개인정보처리방침 URL", description = "S3 presigned URL (10분 유효)", security = {})
    @GetMapping("/privacy")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPrivacyUrl() {
        String url = termsService.getPrivacyUrl();
        return ResponseEntity.ok(ApiResponse.success(Map.of("url", url)));
    }
}
