package com.therapyCommunity_Vol1.backend.post.controller;

import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.post.dto.DownloadedPostResponse;
import com.therapyCommunity_Vol1.backend.post.service.PostAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "받은 자료함", description = "다운로드한 자료 목록")
@RestController
@RequestMapping("/api/v1/me/downloads")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MyDownloadController {

    private final PostAttachmentService postAttachmentService;

    @Operation(summary = "받은 자료 목록", description = "다운로드한 자료 게시글 목록 (최신순, 페이징)")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<DownloadedPostResponse>>> getMyDownloads(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PagedResponse<DownloadedPostResponse> response = postAttachmentService.getMyDownloads(
                userDetails.getUserId(),
                page,
                size
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
