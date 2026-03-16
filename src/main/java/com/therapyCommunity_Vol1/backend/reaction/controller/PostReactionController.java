package com.therapyCommunity_Vol1.backend.reaction.controller;

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

@RestController
@RequestMapping("/api/v1/posts/{postId}/reaction")
@RequiredArgsConstructor
public class PostReactionController {

    private final PostReactionService postReactionService;

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
