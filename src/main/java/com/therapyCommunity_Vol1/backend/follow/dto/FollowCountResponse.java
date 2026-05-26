package com.therapyCommunity_Vol1.backend.follow.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FollowCountResponse {

    private long followerCount;
    private long followingCount;
}
