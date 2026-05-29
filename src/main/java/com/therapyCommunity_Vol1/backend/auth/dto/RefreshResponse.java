package com.therapyCommunity_Vol1.backend.auth.dto;

import com.therapyCommunity_Vol1.backend.user.dto.CurrentUserResponse;

public record RefreshResponse(
        String accessToken,
        long accessTokenExpiresInSec,
        CurrentUserResponse user
) {}
