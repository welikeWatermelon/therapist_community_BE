package com.therapyCommunity_Vol1.backend.reaction.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.reaction.dto.CommentReactionStatusResponse;
import com.therapyCommunity_Vol1.backend.reaction.dto.ToggleCommentReactionRequest;
import com.therapyCommunity_Vol1.backend.reaction.service.CommentReactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "댓글 리액션", description = "댓글 좋아요/싫어요 토글")
@RestController
@RequestMapping("/api/v1/comments/{commentId}/reaction")
@RequiredArgsConstructor
public class CommentReactionController {

    private final CommentReactionService commentReactionService;

    @Operation(summary = "리액션 상태 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<CommentReactionStatusResponse>> getReactionStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long commentId
    ) {
        CommentReactionStatusResponse response = commentReactionService.getReactionStatus(
                userDetails.getUser().getId(),
                commentId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "리액션 토글")
    @PutMapping
    public ResponseEntity<ApiResponse<CommentReactionStatusResponse>> toggleReaction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long commentId,
            @Valid @RequestBody ToggleCommentReactionRequest request
    ) {
        CommentReactionStatusResponse response = commentReactionService.toggleReaction(
                userDetails.getUser().getId(),
                commentId,
                request
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
