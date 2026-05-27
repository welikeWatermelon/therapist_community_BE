package com.therapyCommunity_Vol1.backend.follow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FollowStatusResponse {

    private Long userId;
    private boolean following;
}
