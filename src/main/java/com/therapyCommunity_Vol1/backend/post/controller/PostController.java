package com.therapyCommunity_Vol1.backend.post.controller;

import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.post.domain.PostSortType;
import com.therapyCommunity_Vol1.backend.post.dto.CreateTherapyPostRequest;
import com.therapyCommunity_Vol1.backend.post.dto.PostListResponse;
import com.therapyCommunity_Vol1.backend.post.dto.TherapyPostDetailResponse;
import com.therapyCommunity_Vol1.backend.post.dto.UpdateTherapyPostRequest;
import com.therapyCommunity_Vol1.backend.post.service.PostService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<ApiResponse<TherapyPostDetailResponse>> createPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateTherapyPostRequest request
    ) {
        TherapyPostDetailResponse response = postService.createPost(userDetails.getUser().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PostListResponse>> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "LATEST") PostSortType sortType
    ) {

        PostListResponse response = postService.getPosts(page, size, sortType);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<TherapyPostDetailResponse>> getPostDetail(
            @PathVariable Long postId
    ) {
        TherapyPostDetailResponse response = postService.getPostDetail(postId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }


    @PatchMapping("/{postId}")
    public ResponseEntity<ApiResponse<TherapyPostDetailResponse>> updatePost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @Valid @RequestBody UpdateTherapyPostRequest request
            ) {
        postService.updatePost(
                userDetails.getUser().getId(),
                userDetails.getUser().getRole(),
                postId,
                request
        );
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId
    ){
        postService.deletePost(
                userDetails.getUser().getId(),
                userDetails.getUser().getRole(),
                postId
        );
        return ResponseEntity.noContent().build();
    }
}
