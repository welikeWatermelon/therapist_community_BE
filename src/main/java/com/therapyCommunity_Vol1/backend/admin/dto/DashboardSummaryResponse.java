package com.therapyCommunity_Vol1.backend.admin.dto;

import java.util.List;

public record DashboardSummaryResponse(
        long totalUsers,
        long todayNewUsers,
        long totalPosts,
        long todayNewPosts,
        long totalComments,
        long todayNewComments,
        long pendingVerifications,
        List<RoleCountDto> usersByRole
) {
    public record RoleCountDto(String role, long count) {}
}
