package com.therapyCommunity_Vol1.backend.scrap.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.scrap.dto.ScrappedPostResponse;
import com.therapyCommunity_Vol1.backend.scrap.dto.ScrapStatusResponse;
import com.therapyCommunity_Vol1.backend.scrap.service.ScrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "스크랩", description = "게시글 스크랩 추가/삭제/목록")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ScrapController {

    private final ScrapService scrapService;

    @Operation(summary = "스크랩 상태 조회")
    @GetMapping("/posts/{postId}/scrap")
    public ResponseEntity<ApiResponse<ScrapStatusResponse>> getScrapStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId
    ) {
        ScrapStatusResponse response = scrapService.getScrapStatus(
                userDetails.getUserId(),
                userDetails.getUserRole(),
                postId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "스크랩 추가")
    @PostMapping("/posts/{postId}/scrap")
    public ResponseEntity<ApiResponse<ScrapStatusResponse>> addScrap(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId
    ) {
        ScrapStatusResponse response = scrapService.addScrap(
                userDetails.getUserId(),
                userDetails.getUserRole(),
                postId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "스크랩 삭제")
    @DeleteMapping("/posts/{postId}/scrap")
    public ResponseEntity<ApiResponse<ScrapStatusResponse>> removeScrap(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId
    ) {
        ScrapStatusResponse response = scrapService.removeScrap(
                userDetails.getUserId(),
                userDetails.getUserRole(),
                postId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "내 스크랩 목록", description = "스크랩한 게시글 목록 (페이징)")
    @GetMapping("/me/scraps")
    public ResponseEntity<ApiResponse<PagedResponse<ScrappedPostResponse>>> getMyScraps(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PagedResponse<ScrappedPostResponse> response = scrapService.getMyScraps(
                userDetails.getUserId(),
                userDetails.getUserRole(),
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
