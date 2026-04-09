package com.therapyCommunity_Vol1.backend.user.controller;

import com.therapyCommunity_Vol1.backend.application.mypage.MyPageFacade;
import com.therapyCommunity_Vol1.backend.application.mypage.dto.MyCommentResponse;
import com.therapyCommunity_Vol1.backend.auth.support.RefreshTokenCookieManager;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.post.dto.TherapyPostSummaryResponse;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.dto.CurrentUserResponse;
import com.therapyCommunity_Vol1.backend.user.dto.UpdateProfileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest {

    private MyPageFacade myPageFacade;
    private RefreshTokenCookieManager cookieManager;
    private UserController userController;
    private MockMvc mockMvc;
    private User testUser;

    @BeforeEach
    void setUp() {
        myPageFacade = mock(MyPageFacade.class);
        cookieManager = mock(RefreshTokenCookieManager.class);
        userController = new UserController(myPageFacade, cookieManager);

        testUser = User.builder()
                .id(1L)
                .email("user@example.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        HandlerMethodArgumentResolver authResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return new CustomUserDetails(testUser);
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setCustomArgumentResolvers(authResolver)
                .build();
    }

    @Test
    void 내정보를_조회하면_현재_유저_요약을_반환한다() {
        CurrentUserResponse currentUserResponse = new CurrentUserResponse(
                1L, "user@example.com", "tester", null, "USER", false, "PUBLIC_ONLY",
                new CurrentUserResponse.TherapistVerificationSummary("NOT_REQUESTED", null, null, null)
        );

        when(myPageFacade.getCurrentUser(1L)).thenReturn(currentUserResponse);

        ResponseEntity<ApiResponse<CurrentUserResponse>> response =
                userController.getCurrentUser(new CustomUserDetails(testUser));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().getData().email()).isEqualTo("user@example.com");
        verify(myPageFacade).getCurrentUser(1L);
    }

    @Test
    void 내_게시글_조회시_facade에_위임한다() throws Exception {
        PagedResponse<TherapyPostSummaryResponse> pagedResponse = new PagedResponse<>(
                List.of(), 0, 10, 0L, 0, false
        );
        when(myPageFacade.getMyPosts(1L, 0, 10)).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/me/posts")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray());

        verify(myPageFacade).getMyPosts(1L, 0, 10);
    }

    @Test
    void 내_댓글_조회시_facade에_위임한다() throws Exception {
        PagedResponse<MyCommentResponse> pagedResponse = new PagedResponse<>(
                List.of(), 0, 10, 0L, 0, false
        );
        when(myPageFacade.getMyComments(1L, 0, 10)).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/me/comments")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray());

        verify(myPageFacade).getMyComments(1L, 0, 10);
    }

    @Test
    void 프로필_수정시_facade에_위임한다() throws Exception {
        CurrentUserResponse currentUserResponse = new CurrentUserResponse(
                1L, "user@example.com", "새닉네임", null, "THERAPIST", true, "FULL",
                new CurrentUserResponse.TherapistVerificationSummary("APPROVED", null, null, null)
        );
        when(myPageFacade.updateProfile(eq(1L), any(UpdateProfileRequest.class))).thenReturn(currentUserResponse);

        mockMvc.perform(patch("/api/v1/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"새닉네임\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"));

        verify(myPageFacade).updateProfile(eq(1L), any(UpdateProfileRequest.class));
    }

    @Test
    void 프로필_이미지_업로드시_facade에_위임한다() throws Exception {
        when(myPageFacade.uploadProfileImage(eq(1L), any())).thenReturn("https://example.com/image.jpg");

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "image".getBytes());

        mockMvc.perform(multipart("/api/v1/me/profile-image").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://example.com/image.jpg"));

        verify(myPageFacade).uploadProfileImage(eq(1L), any());
    }

    @Test
    void 프로필_이미지_조회시_facade에_위임한다() throws Exception {
        StoredFileResource storedFile = new StoredFileResource(
                new ByteArrayResource("image".getBytes()), "image/jpeg", "photo.jpg"
        );
        when(myPageFacade.loadProfileImage("abc.jpg")).thenReturn(storedFile);

        mockMvc.perform(get("/api/v1/me/profile-image/profile-images/abc.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));

        verify(myPageFacade).loadProfileImage("abc.jpg");
    }

    @Test
    void 회원탈퇴시_facade에_위임하고_쿠키를_만료시킨다() throws Exception {
        mockMvc.perform(delete("/api/v1/me"))
                .andExpect(status().isNoContent());

        verify(myPageFacade).withdraw(1L);
        verify(cookieManager).expireRefreshTokenCookie(any());
    }
}
