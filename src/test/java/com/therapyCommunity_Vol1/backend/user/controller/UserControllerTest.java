package com.therapyCommunity_Vol1.backend.user.controller;

import com.therapyCommunity_Vol1.backend.auth.support.RefreshTokenCookieManager;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.dto.CurrentUserResponse;
import com.therapyCommunity_Vol1.backend.user.mypage.MyPageFacade;
import com.therapyCommunity_Vol1.backend.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    @Test
    void 내정보를_조회하면_현재_유저_요약을_반환한다() {
        MyPageFacade myPageFacade = mock(MyPageFacade.class);
        UserService userService = mock(UserService.class);
        RefreshTokenCookieManager cookieManager = mock(RefreshTokenCookieManager.class);
        UserController userController = new UserController(myPageFacade, userService, cookieManager);
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .nickname("tester")
                .role(UserRole.USER)
                .build();
        CurrentUserResponse currentUserResponse = new CurrentUserResponse(
                1L,
                "user@example.com",
                "tester",
                null,
                "USER",
                false,
                new CurrentUserResponse.TherapistVerificationSummary("NOT_REQUESTED", null, null, null)
        );

        when(myPageFacade.getCurrentUser(1L)).thenReturn(currentUserResponse);

        ResponseEntity<ApiResponse<CurrentUserResponse>> response =
                userController.getCurrentUser(new CustomUserDetails(user));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().email()).isEqualTo("user@example.com");
        verify(myPageFacade).getCurrentUser(1L);
    }
}
