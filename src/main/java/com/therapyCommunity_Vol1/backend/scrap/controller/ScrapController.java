package com.therapyCommunity_Vol1.backend.scrap.controller;

import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.scrap.dto.ScrapListResponse;
import com.therapyCommunity_Vol1.backend.scrap.dto.ScrapStatusResponse;
import com.therapyCommunity_Vol1.backend.scrap.service.ScrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ScrapController {

    private final ScrapService scrapService;

    @GetMapping("/posts/{postId}/scrap")
    public ResponseEntity<ApiResponse<ScrapStatusResponse>> getScrapStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId
    ) {
        ScrapStatusResponse response = scrapService.getScrapStatus(
                userDetails.getUser().getId(),
                postId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/posts/{postId}/scrap")
    public ResponseEntity<ApiResponse<ScrapStatusResponse>> addScrap(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId
    ) {
        ScrapStatusResponse response = scrapService.addScrap(
                userDetails.getUser().getId(),
                postId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/posts/{postId}/scrap")
    public ResponseEntity<ApiResponse<ScrapStatusResponse>> removeScrap(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId
    ) {
        ScrapStatusResponse response = scrapService.removeScrap(
                userDetails.getUser().getId(),
                postId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me/scraps")
    public ResponseEntity<ApiResponse<ScrapListResponse>> getMyScraps(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        ScrapListResponse response = scrapService.getMyScraps(
                userDetails.getUser().getId(),
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
