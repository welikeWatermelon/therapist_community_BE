package com.therapyCommunity_Vol1.backend.follow.dto;

import com.therapyCommunity_Vol1.backend.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FollowUserResponse {

    private Long userId;
    private String nickname;
    private String profileImageUrl;
    private String role;

    public static FollowUserResponse from(User user) {
        return new FollowUserResponse(
                user.getId(),
                user.getDisplayNickname(),
                user.getProfileImageUrl(),
                user.getRole().getCode()
        );
    }
}
