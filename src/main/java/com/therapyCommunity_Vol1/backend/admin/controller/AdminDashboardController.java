package com.therapyCommunity_Vol1.backend.admin.controller;

import com.therapyCommunity_Vol1.backend.admin.dto.DashboardPostStatsResponse;
import com.therapyCommunity_Vol1.backend.admin.dto.DashboardSummaryResponse;
import com.therapyCommunity_Vol1.backend.admin.dto.DashboardUserStatsResponse;
import com.therapyCommunity_Vol1.backend.admin.service.AdminDashboardService;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 — 대시보드", description = "관리자 대시보드 통계")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(summary = "대시보드 요약", description = "전체 유저/게시글/댓글 수, 오늘 신규, 인증 대기, 역할 분포")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getSummary()));
    }

    @Operation(summary = "게시글 통계", description = "치료영역별/연령대별/유형별 게시글 수, 영역별 평균 조회수")
    @GetMapping("/posts/stats")
    public ResponseEntity<ApiResponse<DashboardPostStatsResponse>> getPostStats() {
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getPostStats()));
    }

    @Operation(summary = "유저 통계", description = "최근 30일 일별 가입 추이, 역할별 분포")
    @GetMapping("/users/stats")
    public ResponseEntity<ApiResponse<DashboardUserStatsResponse>> getUserStats() {
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getUserStats()));
    }
}
