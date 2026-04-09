package com.therapyCommunity_Vol1.backend.post.controller;

import com.therapyCommunity_Vol1.backend.global.common.CursorPagedResponse;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.post.domain.PostSortType;
import com.therapyCommunity_Vol1.backend.post.dto.PostSearchCondition;
import com.therapyCommunity_Vol1.backend.post.dto.TherapyPostSummaryResponse;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PostControllerTest {

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
                .build();
    }

    @Test
    void 게시글_목록_조회_성공() throws Exception {
        // given
        PagedResponse<TherapyPostSummaryResponse> serviceResponse = new PagedResponse<>(
                List.of(),
                0,
                10,
                0L,
                0,
                false
        );
        given(postService.getPosts(eq(0), eq(10), eq(PostSortType.LATEST), any(PostSearchCondition.class), any(UserRole.class)))
                .willReturn(serviceResponse);

        // when
        mockMvc.perform(get("/api/v1/posts")
                        .queryParam("page", "0")
                        .queryParam("size", "10")
                        .queryParam("sortType", "LATEST")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0))
                .andExpect(jsonPath("$.data.items").isArray());

        verify(postService).getPosts(eq(0), eq(10), eq(PostSortType.LATEST), any(PostSearchCondition.class), any(UserRole.class));
    }

    @Test
    void 피드_조회_성공() throws Exception {
        // given
        CursorPagedResponse<TherapyPostSummaryResponse> feedResponse = new CursorPagedResponse<>(
                List.of(),
                null,
                false,
                20
        );
        given(postService.getPostsFeed(eq(20), eq(null), any(UserRole.class)))
                .willReturn(feedResponse);

        // when & then
        mockMvc.perform(get("/api/v1/posts/feed")
                        .queryParam("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.data.size").value(20));

        verify(postService).getPostsFeed(eq(20), eq(null), any(UserRole.class));
    }
}
