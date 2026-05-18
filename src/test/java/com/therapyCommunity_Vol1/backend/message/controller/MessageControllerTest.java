package com.therapyCommunity_Vol1.backend.message.controller;

import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.message.dto.MessageResponse;
import com.therapyCommunity_Vol1.backend.message.dto.UnreadCountResponse;
import com.therapyCommunity_Vol1.backend.message.service.MessageService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MessageControllerTest {

    private MessageService messageService;
    private MockMvc mockMvc;
    private User testUser;

    @BeforeEach
    void setUp() {
        messageService = mock(MessageService.class);
        MessageController controller = new MessageController(messageService);

        testUser = User.builder()
                .id(1L)
                .email("user@test.com")
                .nickname("테스터")
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

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(authResolver)
                .build();
    }

    @Test
    void 쪽지_발송_성공() throws Exception {
        MessageResponse response = new MessageResponse(
                1L, 1L, "테스터", 2L, "수신자", "안녕하세요", false, false, LocalDateTime.now());
        when(messageService.sendMessage(eq(1L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\": 2, \"content\": \"안녕하세요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("안녕하세요"));

        verify(messageService).sendMessage(eq(1L), any());
    }

    @Test
    void 빈_내용으로_쪽지_발송시_400() throws Exception {
        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\": 2, \"content\": \"\"}"))
                .andExpect(status().isBadRequest());

        verify(messageService, never()).sendMessage(any(), any());
    }

    @Test
    void 수신자_없이_쪽지_발송시_400() throws Exception {
        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"테스트\"}"))
                .andExpect(status().isBadRequest());

        verify(messageService, never()).sendMessage(any(), any());
    }

    @Test
    void 천자_초과_내용으로_쪽지_발송시_400() throws Exception {
        String longContent = "가".repeat(1001);

        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\": 2, \"content\": \"" + longContent + "\"}"))
                .andExpect(status().isBadRequest());

        verify(messageService, never()).sendMessage(any(), any());
    }

    @Test
    void 천자_내용으로_쪽지_발송시_성공() throws Exception {
        String maxContent = "가".repeat(1000);
        MessageResponse response = new MessageResponse(
                1L, 1L, "테스터", 2L, "수신자", maxContent, false, false, LocalDateTime.now());
        when(messageService.sendMessage(eq(1L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\": 2, \"content\": \"" + maxContent + "\"}"))
                .andExpect(status().isOk());

        verify(messageService).sendMessage(eq(1L), any());
    }

    @Test
    void 받은_쪽지함_조회_성공() throws Exception {
        PagedResponse<MessageResponse> pagedResponse = new PagedResponse<>(
                List.of(), 0, 20, 0L, 0, false);
        when(messageService.getReceivedMessages(1L, 0, 20)).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/me/messages/received"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray());

        verify(messageService).getReceivedMessages(1L, 0, 20);
    }

    @Test
    void 보낸_쪽지함_조회_성공() throws Exception {
        PagedResponse<MessageResponse> pagedResponse = new PagedResponse<>(
                List.of(), 0, 20, 0L, 0, false);
        when(messageService.getSentMessages(1L, 0, 20)).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/me/messages/sent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray());

        verify(messageService).getSentMessages(1L, 0, 20);
    }

    @Test
    void 쪽지_상세_조회_성공() throws Exception {
        MessageResponse response = new MessageResponse(
                1L, 2L, "발신자", 1L, "테스터", "내용", true, false, LocalDateTime.now());
        when(messageService.getMessage(1L, 1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/messages/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").value("내용"));

        verify(messageService).getMessage(1L, 1L);
    }

    @Test
    void 쪽지_삭제_성공() throws Exception {
        mockMvc.perform(delete("/api/v1/messages/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(messageService).deleteMessage(1L, 1L);
    }

    @Test
    void 안읽은_쪽지_수_조회_성공() throws Exception {
        when(messageService.getUnreadCount(1L)).thenReturn(new UnreadCountResponse(3));

        mockMvc.perform(get("/api/v1/me/messages/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(3));

        verify(messageService).getUnreadCount(1L);
    }

    @Test
    void 공지_쪽지_발송_성공() throws Exception {
        mockMvc.perform(post("/api/v1/admin/messages/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"공지사항입니다\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(messageService).broadcastMessage(eq(1L), any());
    }

    @Test
    void 빈_내용으로_공지_발송시_400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/messages/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"\"}"))
                .andExpect(status().isBadRequest());

        verify(messageService, never()).broadcastMessage(any(), any());
    }
}
