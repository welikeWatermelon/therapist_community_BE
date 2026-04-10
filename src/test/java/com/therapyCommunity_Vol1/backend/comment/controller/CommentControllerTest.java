package com.therapyCommunity_Vol1.backend.comment.controller;

import com.therapyCommunity_Vol1.backend.comment.dto.CommentResponse;
import com.therapyCommunity_Vol1.backend.comment.dto.ReplyCommentResponse;
import com.therapyCommunity_Vol1.backend.comment.service.CommentService;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentControllerTest {

    private final CommentService commentService = mock(CommentService.class);

    private MockMvc mockMvc;

    private User user;

    @BeforeEach
    void setUp() {
        CommentController commentController = new CommentController(commentService);

        user = User.builder()
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

        mockMvc = MockMvcBuilders.standaloneSetup(commentController)
                .setCustomArgumentResolvers(authenticationPrincipalResolver)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json().build()
                ))
                .build();
    }

    @Test
    void 댓글_목록_조회시_json_shape으로_nested_replies를_반환한다() throws Exception {
        ReplyCommentResponse reply = new ReplyCommentResponse(
                2L,
                10L,
                1L,
                2L,
                "reply-user",
                "USER",
                false,
                "대댓글",
                false,
                false,
                false,
                LocalDateTime.of(2026, 3, 16, 10, 5),
                LocalDateTime.of(2026, 3, 16, 10, 5)
        );

        CommentResponse root = new CommentResponse(
                1L,
                10L,
                null,
                1L,
                "tester",
                "THERAPIST",
                false,
                "부모 댓글",
                false,
                true,
                true,
                LocalDateTime.of(2026, 3, 16, 10, 0),
                LocalDateTime.of(2026, 3, 16, 10, 0),
                List.of(reply)
        );

        given(commentService.getComments(1L, UserRole.THERAPIST, 10L)).willReturn(List.of(root));

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", 10L)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].authorId").value(1))
                .andExpect(jsonPath("$.data[0].replies[0].id").value(2))
                .andExpect(jsonPath("$.data[0].replies[0].parentCommentId").value(1))
                .andExpect(jsonPath("$.data[0].replies[0].authorRole").value("USER"))
                .andExpect(jsonPath("$.data[0].replies.length()").value(1));

        verify(commentService).getComments(1L, UserRole.THERAPIST, 10L);
    }
}
