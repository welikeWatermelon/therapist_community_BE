package com.therapyCommunity_Vol1.backend.post.controller;

import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.post.dto.DownloadListResponse;
import com.therapyCommunity_Vol1.backend.post.service.PostAttachmentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/downloads")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MyDownloadController {

    private final PostAttachmentService postAttachmentService;

    @GetMapping
    public ResponseEntity<ApiResponse<DownloadListResponse>> getMyDownloads(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        DownloadListResponse response = postAttachmentService.getMyDownloads(
                userDetails.getUser().getId(),
                page,
                size
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
