package com.therapyCommunity_Vol1.backend.auth.domain;

import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;


class RefreshTokenTest {

    @Test
    void 리프레시토큰을_발급할_수_있다() {

        // given
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.USER)
                .build();

        // when
        RefreshToken refreshToken = RefreshToken.issue(
                user,
                "hashed-token",
                UUID.randomUUID(),
                "Mozilla/5.0",
                "127.0.0.1",
                LocalDateTime.now().plusDays(14)
        );

        // then
        assertThat(refreshToken.getUser()).isEqualTo(user);
        assertThat(refreshToken.getTokenHash()).isEqualTo("hashed-token");
        assertThat(refreshToken.isRevoked()).isFalse();
    }

    @Test
    void 리프레시토큰을_revoke_할_수_있다() {

        // given
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.USER)
                .build();

        RefreshToken refreshToken = RefreshToken.issue(
                user,
                "hashed-token",
                UUID.randomUUID(),
                "Mozilla/5.0",
                "127.0.0.1",
                LocalDateTime.now().plusDays(14)
        );

        // when
        refreshToken.revoke("LOGOUT");

        // then
        assertThat(refreshToken.isRevoked()).isTrue();
        assertThat(refreshToken.getRevokedReason()).isEqualTo("LOGOUT");
    }
}