package com.therapyCommunity_Vol1.backend.auth.dto;

public record RefreshResponse(
        String accessToken,
        long accessTokenExpiresInSec
) {}
