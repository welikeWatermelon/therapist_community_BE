package com.therapyCommunity_Vol1.backend.post.controller;

import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.post.domain.PostSortType;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.dto.CreateTherapyPostRequest;
import com.therapyCommunity_Vol1.backend.post.dto.PostSearchCondition;
import com.therapyCommunity_Vol1.backend.post.dto.TherapyPostDetailResponse;
import com.therapyCommunity_Vol1.backend.post.dto.TherapyPostSummaryResponse;
import com.therapyCommunity_Vol1.backend.post.dto.UpdateTherapyPostRequest;
import com.therapyCommunity_Vol1.backend.post.service.PostService;
import com.therapyCommunity_Vol1.backend.scrap.service.ScrapService;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "게시글", description = "게시글 CRUD + 검색/필터")
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PostController {

    private final PostService postService;
    private final ScrapService scrapService;

    @Operation(summary = "게시글 작성", description = "제목, 본문, 치료영역, 연령대 입력. postType은 첨부파일 유무로 자동 결정")
    @PostMapping
    public ResponseEntity<ApiResponse<TherapyPostDetailResponse>> createPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateTherapyPostRequest request
    ) {
        TherapyPostDetailResponse response = postService.createPost(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @Operation(summary = "게시글 목록/검색", description = "keyword(초성/텍스트), therapyArea, postType 필터. sortType: LATEST, MOST_VIEWED")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TherapyPostSummaryResponse>>> getPosts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "LATEST") PostSortType sortType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) TherapyArea therapyArea,
            @RequestParam(required = false) PostType postType
    ) {
        PostSearchCondition condition = new PostSearchCondition(keyword, therapyArea, postType);
        Long userId = userDetails != null ? userDetails.getUserId() : null;
        UserRole userRole = userDetails != null ? userDetails.getUserRole() : UserRole.USER;
        PagedResponse<TherapyPostSummaryResponse> response = postService.getPosts(page, size, sortType, condition, userRole);

        List<Long> postIds = response.getItems().stream()
                .map(TherapyPostSummaryResponse::getId).toList();
        Set<Long> scrappedIds = scrapService.getScrappedPostIds(userId, postIds);
        response.getItems().forEach(post -> post.markScrapped(scrappedIds.contains(post.getId())));

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "게시글 상세 조회", description = "조회수 자동 증가, 첨부파일 목록 포함")
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<TherapyPostDetailResponse>> getPostDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId
    ) {
        boolean isScrapped = scrapService.getScrappedPostIds(
                userDetails.getUserId(), List.of(postId)).contains(postId);
        TherapyPostDetailResponse response = postService.getPostDetail(
                userDetails.getUserId(),
                userDetails.getUserRole(),
                postId,
                isScrapped
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }


    @Operation(summary = "게시글 수정", description = "작성자 또는 관리자만 수정 가능")
    @PatchMapping("/{postId}")
    public ResponseEntity<ApiResponse<TherapyPostDetailResponse>> updatePost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId,
            @Valid @RequestBody UpdateTherapyPostRequest request
            ) {
        postService.updatePost(
                userDetails.getUserId(),
                userDetails.getUserRole(),
                postId,
                request
        );
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "게시글 삭제", description = "soft delete. 작성자 또는 관리자만 삭제 가능")
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long postId
    ){
        postService.deletePost(
                userDetails.getUserId(),
                userDetails.getUserRole(),
                postId
        );
        return ResponseEntity.noContent().build();
    }
}
