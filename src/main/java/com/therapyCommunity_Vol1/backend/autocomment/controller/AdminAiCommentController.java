package com.therapyCommunity_Vol1.backend.autocomment.controller;

import com.therapyCommunity_Vol1.backend.autocomment.dto.AiCommentDraftResponse;
import com.therapyCommunity_Vol1.backend.autocomment.service.AiCommentReviewService;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI 댓글 리뷰 (Admin)", description = "AI 댓글 초안 조회/승인/거절")
@RestController
@RequestMapping("/api/v1/admin/posts/{postId}/ai-comment-draft")
@RequiredArgsConstructor
public class AdminAiCommentController {

    private final AiCommentReviewService reviewService;

    @Operation(summary = "AI 댓글 초안 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<AiCommentDraftResponse>> getDraft(@PathVariable Long postId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getDraft(postId)));
    }

    @Operation(summary = "AI 댓글 초안 승인", description = "승인 시 AI 계정으로 댓글 생성 + 알림 발행")
    @PostMapping("/approve")
    public ResponseEntity<ApiResponse<AiCommentDraftResponse>> approve(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.approve(postId, userDetails.getUserId())
        ));
    }

    @Operation(summary = "AI 댓글 초안 거절")
    @PostMapping("/reject")
    public ResponseEntity<ApiResponse<AiCommentDraftResponse>> reject(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.reject(postId, userDetails.getUserId())
        ));
    }
}
