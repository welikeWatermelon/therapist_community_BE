package com.therapyCommunity_Vol1.backend.admin.controller;

import com.therapyCommunity_Vol1.backend.admin.dto.AdminCommentListResponse;
import com.therapyCommunity_Vol1.backend.admin.dto.AdminPostListResponse;
import com.therapyCommunity_Vol1.backend.admin.dto.ChangePostVisibilityRequest;
import com.therapyCommunity_Vol1.backend.admin.service.AdminContentService;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "관리자 — 콘텐츠 관리", description = "게시글/댓글 관리 (목록/숨김/삭제)")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminContentController {

    private final AdminContentService adminContentService;

    @Operation(summary = "게시글 목록", description = "키워드(content), 치료영역, 유형, 공개여부 필터. 삭제된 게시글 포함")
    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<PagedResponse<AdminPostListResponse>>> getPosts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) TherapyArea therapyArea,
            @RequestParam(required = false) PostType postType,
            @RequestParam(required = false) Visibility visibility,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PagedResponse<AdminPostListResponse> response =
                adminContentService.getPosts(keyword, therapyArea, postType, visibility, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "게시글 공개여부 변경", description = "PUBLIC ↔ PRIVATE 전환")
    @PatchMapping("/posts/{id}/visibility")
    public ResponseEntity<ApiResponse<AdminPostListResponse>> changePostVisibility(
            @PathVariable Long id,
            @Valid @RequestBody ChangePostVisibilityRequest request
    ) {
        AdminPostListResponse response =
                adminContentService.changePostVisibility(id, request.getVisibility());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "게시글 삭제", description = "soft delete")
    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        adminContentService.deletePost(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "댓글 목록", description = "게시글 ID 필터(선택). 삭제된 댓글 포함")
    @GetMapping("/comments")
    public ResponseEntity<ApiResponse<PagedResponse<AdminCommentListResponse>>> getComments(
            @RequestParam(required = false) Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PagedResponse<AdminCommentListResponse> response =
                adminContentService.getComments(postId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "댓글 삭제", description = "soft delete")
    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id) {
        adminContentService.deleteComment(id);
        return ResponseEntity.noContent().build();
    }
}
