package com.therapyCommunity_Vol1.backend.global.security;

import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import com.therapyCommunity_Vol1.backend.global.cache.UserCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class CustomUserDetailsServiceTest {

    UserRepository userRepository;
    UserCacheService userCacheService;
    CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        userCacheService = Mockito.mock(UserCacheService.class);
        service = new CustomUserDetailsService(userRepository, userCacheService);
    }

    @Test
    void userId로_사용자_조회_성공() {
        // given
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.USER)
                .build();

        Mockito.when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        //when
        CustomUserDetails details = (CustomUserDetails) service.loadUserByUsername("1");

        // then
        assertThat(details.getUser().getId()).isEqualTo(1L);
        assertThat(details.getAuthorities()).extracting("authority").contains("ROLE_USER");
    }

    @Test
    void 사용자_없으면_예외() {
        // given
        Mockito.when(userRepository.findById(1L))
                .thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> service.loadUserByUsername("1"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
