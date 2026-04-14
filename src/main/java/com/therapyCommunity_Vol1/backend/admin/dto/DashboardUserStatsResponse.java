package com.therapyCommunity_Vol1.backend.admin.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardUserStatsResponse(
        List<DailySignupDto> dailySignups,
        List<DashboardSummaryResponse.RoleCountDto> usersByRole
) {
    public record DailySignupDto(LocalDate date, long count) {}
}
