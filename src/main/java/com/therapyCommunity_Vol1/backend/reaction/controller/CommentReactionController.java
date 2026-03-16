package com.therapyCommunity_Vol1.backend.reaction.controller;

import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.reaction.dto.CommentReactionStatusResponse;
import com.therapyCommunity_Vol1.backend.reaction.dto.ToggleCommentReactionRequest;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostCommentReactionRepository;
import com.therapyCommunity_Vol1.backend.reaction.service.CommentReactionService;
import com.therapyCommunity_Vol1.backend.reaction.service.PostReactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/comments/{commentId}/reaction")
@RequiredArgsConstructor
public class CommentReactionController {

    private final CommentReactionService commentReactionService;
    private final TherapyPostCommentReactionRepository therapyPostCommentReactionRepository;
    private final PostReactionService postReactionService;

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
