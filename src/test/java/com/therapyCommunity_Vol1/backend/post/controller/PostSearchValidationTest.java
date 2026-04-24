package com.therapyCommunity_Vol1.backend.post.controller;

import com.therapyCommunity_Vol1.backend.global.exception.GlobalExceptionHandler;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.post.dto.SearchCursorResponse;
import com.therapyCommunity_Vol1.backend.post.service.PostService;
import com.therapyCommunity_Vol1.backend.scrap.service.ScrapService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostSearchValidationTest {

    @Mock
    private PostService postService;

    @Mock
    private ScrapService scrapService;

    @InjectMocks
    private PostController postController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
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
                return new CustomUserDetails(user);
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(postController)
                .setCustomArgumentResolvers(authResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 키워드_1글자_400() throws Exception {
        mockMvc.perform(get("/api/v1/posts/search")
                        .param("keyword", "a"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 키워드_빈문자열_400() throws Exception {
        mockMvc.perform(get("/api/v1/posts/search")
                        .param("keyword", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 키워드_공백만_400() throws Exception {
        mockMvc.perform(get("/api/v1/posts/search")
                        .param("keyword", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 키워드_101자_400() throws Exception {
        String longKeyword = "가".repeat(101);
        mockMvc.perform(get("/api/v1/posts/search")
                        .param("keyword", longKeyword))
                .andExpect(status().isBadRequest());
    }

    @Test
    void size_51_400() throws Exception {
        mockMvc.perform(get("/api/v1/posts/search")
                        .param("keyword", "감각통합")
                        .param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void size_0_400() throws Exception {
        mockMvc.perform(get("/api/v1/posts/search")
                        .param("keyword", "감각통합")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 커서_lastScore만_전달_400() throws Exception {
        mockMvc.perform(get("/api/v1/posts/search")
                        .param("keyword", "감각통합")
                        .param("lastScore", "0.5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 커서_lastId만_전달_400() throws Exception {
        mockMvc.perform(get("/api/v1/posts/search")
                        .param("keyword", "감각통합")
                        .param("lastId", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 키워드_100자_경계값_200() throws Exception {
        String keyword100 = "가".repeat(100);
        SearchCursorResponse response = new SearchCursorResponse(
                List.of(), new SearchCursorResponse.SearchCursorMeta(false, null, null));
        given(postService.searchPostsByRelevance(any(), any(), any(), any(int.class), any()))
                .willReturn(response);
        given(scrapService.getScrappedPostIds(any(), anyList()))
                .willReturn(Set.of());

        mockMvc.perform(get("/api/v1/posts/search")
                        .param("keyword", keyword100))
                .andExpect(status().isOk());
    }

    @Test
    void size_1_경계값_200() throws Exception {
        SearchCursorResponse response = new SearchCursorResponse(
                List.of(), new SearchCursorResponse.SearchCursorMeta(false, null, null));
        given(postService.searchPostsByRelevance(any(), any(), any(), any(int.class), any()))
                .willReturn(response);
        given(scrapService.getScrappedPostIds(any(), anyList()))
                .willReturn(Set.of());

        mockMvc.perform(get("/api/v1/posts/search")
                        .param("keyword", "감각통합")
                        .param("size", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void 정상_케이스_200() throws Exception {
        SearchCursorResponse response = new SearchCursorResponse(
                List.of(), new SearchCursorResponse.SearchCursorMeta(false, null, null));
        given(postService.searchPostsByRelevance(any(), any(), any(), any(int.class), any()))
                .willReturn(response);
        given(scrapService.getScrappedPostIds(any(), anyList()))
                .willReturn(Set.of());

        mockMvc.perform(get("/api/v1/posts/search")
                        .param("keyword", "감각통합")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }
}
