package com.therapyCommunity_Vol1.backend.admin.service;

import com.therapyCommunity_Vol1.backend.admin.dto.DashboardPostStatsResponse;
import com.therapyCommunity_Vol1.backend.admin.dto.DashboardSummaryResponse;
import com.therapyCommunity_Vol1.backend.admin.dto.DashboardUserStatsResponse;
import com.therapyCommunity_Vol1.backend.comment.service.CommentService;
import com.therapyCommunity_Vol1.backend.post.service.PostService;
import com.therapyCommunity_Vol1.backend.therapist.service.TherapistVerificationService;
import com.therapyCommunity_Vol1.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final UserService userService;
    private final PostService postService;
    private final CommentService commentService;
    private final TherapistVerificationService therapistVerificationService;

    public DashboardSummaryResponse getSummary() {
        return new DashboardSummaryResponse(
                userService.countActiveUsers(),
                userService.countTodayNewUsers(),
                postService.countActivePosts(),
                postService.countTodayNewPosts(),
                commentService.countActiveComments(),
                commentService.countTodayNewComments(),
                therapistVerificationService.countPendingVerifications(),
                toRoleCounts(userService.countUsersByRole())
        );
    }

    public DashboardPostStatsResponse getPostStats() {
        return new DashboardPostStatsResponse(
                toTherapyAreaStats(postService.countPostsByTherapyArea()),
                toTherapyAreaAvgViews(postService.avgViewCountByTherapyArea()),
                toAgeGroupStats(postService.countPostsByAgeGroup()),
                toPostTypeStats(postService.countPostsByPostType())
        );
    }

    public DashboardUserStatsResponse getUserStats() {
        return new DashboardUserStatsResponse(
                toDailySignups(userService.getDailySignupCounts(30)),
                toRoleCounts(userService.countUsersByRole())
        );
    }

    private List<DashboardSummaryResponse.RoleCountDto> toRoleCounts(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new DashboardSummaryResponse.RoleCountDto(
                        row[0] != null ? row[0].toString() : null,
                        ((Number) row[1]).longValue()
                ))
                .toList();
    }

    private List<DashboardPostStatsResponse.TherapyAreaStatDto> toTherapyAreaStats(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new DashboardPostStatsResponse.TherapyAreaStatDto(
                        row[0] != null ? row[0].toString() : null,
                        ((Number) row[1]).longValue()
                ))
                .toList();
    }

    private List<DashboardPostStatsResponse.TherapyAreaAvgViewDto> toTherapyAreaAvgViews(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new DashboardPostStatsResponse.TherapyAreaAvgViewDto(
                        row[0] != null ? row[0].toString() : null,
                        row[1] != null ? ((Number) row[1]).doubleValue() : 0.0
                ))
                .toList();
    }

    private List<DashboardPostStatsResponse.AgeGroupStatDto> toAgeGroupStats(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new DashboardPostStatsResponse.AgeGroupStatDto(
                        row[0] != null ? row[0].toString() : null,
                        ((Number) row[1]).longValue()
                ))
                .toList();
    }

    private List<DashboardPostStatsResponse.PostTypeStatDto> toPostTypeStats(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new DashboardPostStatsResponse.PostTypeStatDto(
                        row[0] != null ? row[0].toString() : null,
                        ((Number) row[1]).longValue()
                ))
                .toList();
    }

    private List<DashboardUserStatsResponse.DailySignupDto> toDailySignups(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new DashboardUserStatsResponse.DailySignupDto(
                        toLocalDate(row[0]),
                        ((Number) row[1]).longValue()
                ))
                .toList();
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate ld) return ld;
        if (value instanceof Date sqlDate) return sqlDate.toLocalDate();
        if (value instanceof java.util.Date utilDate) return new Date(utilDate.getTime()).toLocalDate();
        return LocalDate.parse(value.toString());
    }
}
