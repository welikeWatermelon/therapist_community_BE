package com.therapyCommunity_Vol1.backend.auth.dto;

import com.therapyCommunity_Vol1.backend.therapist.dto.TherapistVerificationStatusDto;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.dto.CurrentUserResponse;
import com.therapyCommunity_Vol1.backend.user.support.ProfileImageUrlAssembler;

import java.util.Optional;

public record LoginResponse(
        CurrentUserResponse user,
        Tokens tokens
) {

    public static LoginResponse of(
            User user,
            Optional<TherapistVerificationStatusDto> verification,
            String accessToken,
            long accessTokenExpiresInSec,
            ProfileImageUrlAssembler profileImageUrlAssembler
    ) {
        return new LoginResponse(
                CurrentUserResponse.from(user, verification, profileImageUrlAssembler),
                new Tokens(accessToken, accessTokenExpiresInSec)
        );
    }

    public record Tokens(
            String accessToken,
            long accessTokenExpiresInSec
    ) {}
}
