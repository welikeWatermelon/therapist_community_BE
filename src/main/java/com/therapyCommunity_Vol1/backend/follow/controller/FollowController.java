package com.therapyCommunity_Vol1.backend.follow.controller;

import com.therapyCommunity_Vol1.backend.follow.dto.FollowStatusResponse;
import com.therapyCommunity_Vol1.backend.follow.service.FollowService;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "팔로우", description = "팔로우/언팔로우")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/{userId}/follow")
@SecurityRequirement(name = "bearerAuth")
public class FollowController {

    private final FollowService followService;

    @Operation(summary = "팔로우 상태 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<FollowStatusResponse>> getFollowStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long userId
    ) {
        FollowStatusResponse response = followService.getFollowStatus(
                userDetails.getUserId(), userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "팔로우")
    @PostMapping
    public ResponseEntity<ApiResponse<FollowStatusResponse>> follow(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long userId
    ) {
        FollowStatusResponse response = followService.follow(
                userDetails.getUserId(), userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "언팔로우")
    @DeleteMapping
    public ResponseEntity<ApiResponse<FollowStatusResponse>> unfollow(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long userId
    ) {
        FollowStatusResponse response = followService.unfollow(
                userDetails.getUserId(), userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
