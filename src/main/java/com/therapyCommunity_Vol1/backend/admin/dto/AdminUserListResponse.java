package com.therapyCommunity_Vol1.backend.admin.dto;

import com.therapyCommunity_Vol1.backend.user.domain.User;

import java.time.LocalDateTime;

public record AdminUserListResponse(
        Long id,
        String email,
        String nickname,
        String profileImageUrl,
        String role,
        LocalDateTime createdAt,
        boolean withdrawn
) {
    public static AdminUserListResponse from(User user) {
        return new AdminUserListResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getRole().getCode(),
                user.getCreatedAt(),
                user.isWithdrawn()
        );
    }
}
