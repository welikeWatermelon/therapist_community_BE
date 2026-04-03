package com.therapyCommunity_Vol1.backend.reaction.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.reaction.dto.PostReactionStatusResponse;
import com.therapyCommunity_Vol1.backend.reaction.dto.TogglePostReactionRequest;
import com.therapyCommunity_Vol1.backend.reaction.service.PostReactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "게시글 리액션", description = "게시글 좋아요/싫어요 토글")
@RestController
@RequestMapping("/api/v1/posts/{postId}/reaction")
@RequiredArgsConstructor
public class PostReactionController {

    private final PostReactionService postReactionService;

    @Operation(summary = "리액션 상태 조회", description = "현재 유저의 리액션 상태와 전체 카운트")
    @GetMapping
    public ResponseEntity<ApiResponse<PostReactionStatusResponse>> getReactionStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId
    ) {
        PostReactionStatusResponse response = postReactionService.getReactionStatus(
                userDetails.getUser().getId(),
                postId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "리액션 토글", description = "같은 타입 재요청 시 취소, 다른 타입이면 전환")
    @PutMapping
    public ResponseEntity<ApiResponse<PostReactionStatusResponse>> toggleReaction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @Valid @RequestBody TogglePostReactionRequest request
    ) {
        PostReactionStatusResponse response = postReactionService.toggleReaction(
                userDetails.getUser().getId(),
                postId,
                request
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
