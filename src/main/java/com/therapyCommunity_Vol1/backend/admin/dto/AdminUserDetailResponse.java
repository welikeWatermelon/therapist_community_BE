package com.therapyCommunity_Vol1.backend.admin.dto;

import com.therapyCommunity_Vol1.backend.user.domain.User;

import java.time.LocalDateTime;

public record AdminUserDetailResponse(
        Long id,
        String email,
        String nickname,
        String profileImageUrl,
        String role,
        LocalDateTime createdAt,
        boolean withdrawn,
        LocalDateTime deletedAt,
        ActivitySummary activitySummary
) {
    public record ActivitySummary(long postCount, long commentCount, long scrapCount) {}

    public static AdminUserDetailResponse from(User user, long postCount, long commentCount, long scrapCount) {
        return new AdminUserDetailResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getRole().getCode(),
                user.getCreatedAt(),
                user.isWithdrawn(),
                user.getDeletedAt(),
                new ActivitySummary(postCount, commentCount, scrapCount)
        );
    }
}
