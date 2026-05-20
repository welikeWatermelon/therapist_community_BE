package com.therapyCommunity_Vol1.backend.post.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.post.dto.PostImageResponse;
import com.therapyCommunity_Vol1.backend.post.dto.UploadConfirmResponse;
import com.therapyCommunity_Vol1.backend.post.dto.UploadInitResponse;
import com.therapyCommunity_Vol1.backend.post.service.upload.MediaKind;
import com.therapyCommunity_Vol1.backend.post.service.upload.UploadConfirmService;
import com.therapyCommunity_Vol1.backend.post.service.upload.UploadInitService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PostUploadControllerTest {

    private final UploadInitService uploadInitService = mock(UploadInitService.class);
    private final UploadConfirmService uploadConfirmService = mock(UploadConfirmService.class);

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @BeforeEach
    void setUp() {
        PostUploadController controller = new PostUploadController(uploadInitService, uploadConfirmService);

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
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return new CustomUserDetails(user);
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(authenticationPrincipalResolver)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void init_returns201_withUploadUrlAndStoredKey() throws Exception {
        when(uploadInitService.init(anyLong(), any(), anyLong(), any(), any(), any(), anyLong(), any()))
                .thenReturn(new UploadInitResponse(
                        "https://s3.example.com/signed",
                        "uploads-pending/images/10/abc.jpg",
                        Instant.parse("2030-01-01T00:00:00Z")
                ));

        mockMvc.perform(post("/api/v1/posts/{postId}/uploads/init", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "IMAGE",
                                "originalFilename", "photo.jpg",
                                "contentType", "image/jpeg",
                                "sizeBytes", 102400
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.uploadUrl").value("https://s3.example.com/signed"))
                .andExpect(jsonPath("$.data.storedKey").value("uploads-pending/images/10/abc.jpg"));

        verify(uploadInitService).init(1L, UserRole.THERAPIST, 10L, MediaKind.IMAGE,
                "photo.jpg", "image/jpeg", 102400L, null);
    }

    @Test
    void init_returns400_onMissingKind() throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/uploads/init", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "originalFilename", "photo.jpg",
                                "contentType", "image/jpeg",
                                "sizeBytes", 102400
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void init_returns400_onZeroSize() throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/uploads/init", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "IMAGE",
                                "originalFilename", "photo.jpg",
                                "contentType", "image/jpeg",
                                "sizeBytes", 0
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void init_returns400_onMissingContentType() throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/uploads/init", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "IMAGE",
                                "originalFilename", "photo.jpg",
                                "sizeBytes", 102400
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirm_returns201_withImagePayload() throws Exception {
        when(uploadConfirmService.confirm(anyLong(), any(), anyLong(), any(), any(), any()))
                .thenReturn(UploadConfirmResponse.ofImage(
                        new PostImageResponse(100L, "https://signed", "abc.jpg", 0, LocalDateTime.now())
                ));

        mockMvc.perform(post("/api/v1/posts/{postId}/uploads/confirm", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "IMAGE",
                                "storedKey", "uploads-pending/images/10/abc.jpg",
                                "originalFilename", "abc.jpg"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.kind").value("IMAGE"))
                .andExpect(jsonPath("$.data.image.id").value(100));
    }

    @Test
    void confirm_returns400_onMissingStoredKey() throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/uploads/confirm", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "IMAGE",
                                "originalFilename", "abc.jpg"
                        ))))
                .andExpect(status().isBadRequest());
    }
}
