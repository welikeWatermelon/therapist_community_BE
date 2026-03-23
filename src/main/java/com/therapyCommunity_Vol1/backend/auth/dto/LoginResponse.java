package com.therapyCommunity_Vol1.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.therapist.domain.TherapistVerification;
import com.therapyCommunity_Vol1.backend.user.dto.CurrentUserResponse;

import java.util.Optional;

public record LoginResponse(
        @JsonProperty("isNewUser") boolean isNewUser,
        CurrentUserResponse user,
        Tokens tokens
) {

    public static LoginResponse of(
            boolean isNewUser,
            User user,
            Optional<TherapistVerification> verification,
            String accessToken,
            long accessTokenExpiresInSec
    ) {
        return new LoginResponse(
                isNewUser,
                CurrentUserResponse.from(user, verification),
                new Tokens(accessToken, accessTokenExpiresInSec)
        );
    }

    public record Tokens(
            String accessToken,
            long accessTokenExpiresInSec
    ) {}
}
