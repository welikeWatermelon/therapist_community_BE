package com.therapyCommunity_Vol1.backend.post.controller;

import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.post.domain.AgeGroup;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.dto.DownloadListResponse;
import com.therapyCommunity_Vol1.backend.post.dto.DownloadedPostResponse;
import com.therapyCommunity_Vol1.backend.post.service.PostAttachmentService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MyDownloadControllerTest {

    private final PostAttachmentService postAttachmentService = mock(PostAttachmentService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MyDownloadController controller = new MyDownloadController(postAttachmentService);

        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        HandlerMethodArgumentResolver authenticationPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && parameter.getParameterType().equals(CustomUserDetails.class);
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory
            ) {
                return new CustomUserDetails(user);
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(authenticationPrincipalResolver)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json().build()
                ))
                .build();
    }

    @Test
    void 받은_자료함_목록을_json으로_반환한다() throws Exception {
        DownloadedPostResponse download = new DownloadedPostResponse(
                10L,
                PostType.RESOURCE,
                "자료 제목",
                "자료 요약",
                "author",
                TherapyArea.SPEECH,
                AgeGroup.AGE_6_12,
                LocalDateTime.of(2026, 3, 20, 10, 0),
                LocalDateTime.of(2026, 3, 22, 12, 0),
                3L
        );

        DownloadListResponse response = new DownloadListResponse(
                List.of(download),
                0,
                10,
                1L,
                1,
                false
        );

        given(postAttachmentService.getMyDownloads(1L, 0, 10)).willReturn(response);

        mockMvc.perform(get("/api/v1/me/downloads")
                        .queryParam("page", "0")
                        .queryParam("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.downloads[0].postId").value(10))
                .andExpect(jsonPath("$.data.downloads[0].postType").value("RESOURCE"))
                .andExpect(jsonPath("$.data.downloads[0].downloadCount").value(3))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        verify(postAttachmentService).getMyDownloads(1L, 0, 10);
    }
}
