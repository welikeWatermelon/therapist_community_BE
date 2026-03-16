package com.therapyCommunity_Vol1.backend.global.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider =
                new JwtTokenProvider(
                        "12345678901234567890123456789012",
                        1800
                );
    }
    @Test
    void 토큰_생성된다() {

        //given
        Long userId = 1L;
        String role = "ROLE_USER";

        //when
        String token = jwtTokenProvider.createAccessToken(userId, role);

        //then
        assertThat(token).isNotNull();
        assertThat(token).isNotBlank();
    }

    @Test
    void 토큰에서_userId_추출() {
        //given
        Long userId = 1L;
        String role = "ROLE_USER";
        String token = jwtTokenProvider.createAccessToken(userId, role);

        //when
        Long extractedUserId = jwtTokenProvider.getUserId(token);

        //then
        assertThat(extractedUserId).isEqualTo(userId);
    }

    @Test
    void 토큰에서_role_추출() {
        //given
        Long userId = 1L;
        String role = "ROLE_USER";
        String token = jwtTokenProvider.createAccessToken(userId, role);

        //when
        String extractedRole = jwtTokenProvider.getRole(token);

        //then
        assertThat(extractedRole).isEqualTo(role);
    }

    @Test
    void 토큰_검증_성공() {

        //given
        Long userId = 1L;
        String role = "ROLE_User";
        String token = jwtTokenProvider.createAccessToken(userId, role);

        //when
        boolean valid = jwtTokenProvider.validateToken(token);

        //then
        assertThat(valid).isTrue();
    }

    @Test
    void 토큰_변조되면_검증실패() {
        //given
        Long userId = 1L;
        String role = "ROLE_USER";
        String token = jwtTokenProvider.createAccessToken(userId, role);
        String temperedToken = token + "broken";

        // when
        boolean valid = jwtTokenProvider.validateToken(temperedToken);

        //then
        assertThat(valid).isFalse();
    }



}