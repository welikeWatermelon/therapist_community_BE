package com.therapyCommunity_Vol1.backend.post.controller;

import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.post.service.PostImageService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PostImageControllerTest {

    private final PostImageService postImageService = mock(PostImageService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PostImageController controller = new PostImageController(postImageService);

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
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(
                                Jackson2ObjectMapperBuilder.json().build()
                        ),
                        new ResourceHttpMessageConverter()
                )
                .build();
    }

    @Test
    void 이미지_삭제시_204_NoContent를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/v1/posts/{postId}/images/{imageId}", 10L, 100L))
                .andExpect(status().isNoContent());

        verify(postImageService).deleteImage(1L, UserRole.THERAPIST, 10L, 100L);
    }
}
