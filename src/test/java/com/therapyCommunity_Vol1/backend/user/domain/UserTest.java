package com.therapyCommunity_Vol1.backend.user.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class UserTest {

    @Test
    void user_생성하다() {
        //given
        User user = User.builder()
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.USER)
                .build();

        // then
        assertThat(user.getEmail()).isEqualTo("test@test.com");
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void 사용자를_치료사로_승격할_수_있다() {
        // given
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.USER)
                .build();

        // when
        user.promoteToTherapist();

        // then
        assertThat(user.getRole()).isEqualTo(UserRole.THERAPIST);
    }
}