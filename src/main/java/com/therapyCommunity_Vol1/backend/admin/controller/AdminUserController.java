package com.therapyCommunity_Vol1.backend.admin.controller;

import com.therapyCommunity_Vol1.backend.admin.dto.AdminUserDetailResponse;
import com.therapyCommunity_Vol1.backend.admin.dto.AdminUserListResponse;
import com.therapyCommunity_Vol1.backend.admin.dto.ChangeUserRoleRequest;
import com.therapyCommunity_Vol1.backend.admin.service.AdminUserService;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "관리자 — 유저 관리", description = "유저 목록/상세/역할 변경")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(summary = "유저 목록", description = "키워드(이메일/닉네임), 역할 필터, 페이징")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<AdminUserListResponse>>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserRole role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PagedResponse<AdminUserListResponse> response =
                adminUserService.getUsers(keyword, role, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "유저 상세", description = "유저 정보 + 활동 요약(게시글/댓글/스크랩 수)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUserDetail(
            @PathVariable Long id
    ) {
        AdminUserDetailResponse response = adminUserService.getUserDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "역할 변경", description = "유저 역할 변경 (USER/THERAPIST/ADMIN). 자신 변경 불가, 마지막 ADMIN 강등 불가")
    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> changeUserRole(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ChangeUserRoleRequest request
    ) {
        AdminUserDetailResponse response =
                adminUserService.changeUserRole(userDetails.getUserId(), id, request.getRole());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
