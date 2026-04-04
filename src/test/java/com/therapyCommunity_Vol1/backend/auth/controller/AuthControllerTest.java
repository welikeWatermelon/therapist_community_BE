package com.therapyCommunity_Vol1.backend.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therapyCommunity_Vol1.backend.auth.dto.LoginRequest;
import com.therapyCommunity_Vol1.backend.auth.dto.LoginResponse;
import com.therapyCommunity_Vol1.backend.auth.dto.RefreshResponse;
import com.therapyCommunity_Vol1.backend.auth.service.AuthService;
import com.therapyCommunity_Vol1.backend.auth.support.RefreshTokenCookieManager;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.GlobalExceptionHandler;
import com.therapyCommunity_Vol1.backend.user.dto.CurrentUserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import jakarta.servlet.http.Cookie;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        AuthController authController = new AuthController(
                authService,
                new RefreshTokenCookieManager("refreshToken", "/api/v1/auth", "Lax", false)
        );

        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void 로그인_성공시_user_응답과_refresh_cookie를_반환한다() throws Exception {
        LoginResponse response = new LoginResponse(
                new CurrentUserResponse(
                        1L,
                        "user@example.com",
                        "닉네임",
                        null,
                        "USER",
                        false,
                        new CurrentUserResponse.TherapistVerificationSummary("NOT_REQUESTED", null, null, null)
                ),
                new LoginResponse.Tokens("access-token", 1800L)
        );
        given(authService.login(any(LoginRequest.class), eq("JUnit"), eq("127.0.0.1")))
                .willReturn(new AuthService.LoginResult(response, "refresh-token", 1209600L));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "JUnit")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        })
                        .content(objectMapper.writeValueAsString(new LoginRequest("user@example.com", "password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.data.user.therapistVerification.status").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.data.tokens.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokens.accessTokenExpiresInSec").value(1800))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        allOf(
                                containsString("refreshToken=refresh-token"),
                                containsString("Max-Age=1209600"),
                                containsString("Path=/api/v1/auth"),
                                containsString("HttpOnly"),
                                containsString("SameSite=Lax")
                        )
                ));
    }

    @Test
    void 리프레시_성공시_cookie로_refresh를_읽고_새_cookie를_반환한다() throws Exception {
        given(authService.refresh("refresh-cookie", "JUnit", "127.0.0.1"))
                .willReturn(new AuthService.RefreshResult(
                        new RefreshResponse("new-access-token", 1800L),
                        "rotated-refresh-token",
                        1209600L
                ));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "refresh-cookie"))
                        .header("User-Agent", "JUnit")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.accessTokenExpiresInSec").value(1800))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        allOf(
                                containsString("refreshToken=rotated-refresh-token"),
                                containsString("Max-Age=1209600"),
                                containsString("HttpOnly")
                        )
                ));
    }

    @Test
    void 리프레시_cookie가_없으면_인증예외를_반환한다() throws Exception {
        assertThatThrownBy(() -> mockMvc.perform(post("/api/v1/auth/refresh")).andReturn())
                .hasCauseInstanceOf(CustomException.class);
    }

    @Test
    void 로그아웃시_refresh_cookie를_만료시킨다() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", "refresh-cookie")))
                .andExpect(status().isNoContent())
                .andReturn()
                .getResponse();

        verify(authService).logout("refresh-cookie");
        org.assertj.core.api.Assertions.assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("refreshToken=")
                .contains("Max-Age=0")
                .contains("HttpOnly");
    }
}
