package com.therapyCommunity_Vol1.backend.post.controller;

import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.post.dto.PostAttachmentResponse;
import com.therapyCommunity_Vol1.backend.post.service.PostAttachmentService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PostAttachmentControllerTest {

    private final PostAttachmentService postAttachmentService = mock(PostAttachmentService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PostAttachmentController controller = new PostAttachmentController(postAttachmentService);

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
    void 첨부파일_업로드시_생성응답을_반환한다() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "guide.pdf",
                "application/pdf",
                "%PDF-sample".getBytes()
        );

        PostAttachmentResponse response = new PostAttachmentResponse(
                100L,
                "guide.pdf",
                "application/pdf",
                1234L,
                "pdf",
                "/api/v1/posts/10/attachments/100/download",
                LocalDateTime.of(2026, 3, 22, 10, 0)
        );

        given(postAttachmentService.uploadAttachment(1L, UserRole.THERAPIST, 10L, file))
                .willReturn(response);

        mockMvc.perform(multipart("/api/v1/posts/{postId}/attachments", 10L)
                        .file(file)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.originalFilename").value("guide.pdf"))
                .andExpect(jsonPath("$.data.extension").value("pdf"));

        verify(postAttachmentService).uploadAttachment(1L, UserRole.THERAPIST, 10L, file);
    }

    @Test
    void 첨부파일_다운로드시_attachment_header를_반환한다() throws Exception {
        StoredFileResource storedFile = new StoredFileResource(
                new ByteArrayResource("pdf-data".getBytes()),
                "application/pdf",
                "guide.pdf"
        );

        given(postAttachmentService.downloadAttachment(1L, 10L, 100L)).willReturn(storedFile);

        mockMvc.perform(get("/api/v1/posts/{postId}/attachments/{attachmentId}/download", 10L, 100L))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"guide.pdf\""))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes("pdf-data".getBytes()));

        verify(postAttachmentService).downloadAttachment(1L, 10L, 100L);
    }
}
