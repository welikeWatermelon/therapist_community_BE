package com.therapyCommunity_Vol1.backend.jobpost.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.therapyCommunity_Vol1.backend.global.common.CursorPagedResponse;
import com.therapyCommunity_Vol1.backend.global.security.CustomUserDetails;
import com.therapyCommunity_Vol1.backend.jobpost.domain.*;
import com.therapyCommunity_Vol1.backend.jobpost.dto.*;
import com.therapyCommunity_Vol1.backend.jobpost.service.JobPostService;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JobPostControllerTest {

    private JobPostService jobPostService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        jobPostService = mock(JobPostService.class);
    }

    private MockMvc mockMvc(CustomUserDetails principal) {
        JobPostController controller = new JobPostController(jobPostService);
        HandlerMethodArgumentResolver resolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }
            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return principal;
            }
        };
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(resolver)
                .setMessageConverters(converter)
                .build();
    }

    private CustomUserDetails principal(Long id, UserRole role) {
        return new CustomUserDetails(User.builder()
                .id(id).email("u@test.com").nickname("u").role(role).build());
    }

    private JobPostDetailResponse detail() {
        JobPost post = JobPost.create(
                User.builder().id(1L).nickname("작성자").role(UserRole.USER).build(),
                "멜론기관", "<p>c</p>", TherapyArea.SPEECH, EmploymentType.FULL_TIME, Region.SEOUL,
                "협의", null, null, "https://example.com", LocalDate.now().plusDays(5));
        return JobPostDetailResponse.from(post, LocalDate.now(), 1L, UserRole.USER);
    }

    @Test
    void 비인증_상세조회는_200이다() throws Exception {
        when(jobPostService.getDetail(eq(100L), isNull(), isNull())).thenReturn(detail());

        mockMvc(null).perform(get("/api/v1/job-posts/100").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.organizationName").value("멜론기관"));
    }

    @Test
    void 목록조회는_200이고_커서응답을_담는다() throws Exception {
        when(jobPostService.getJobPosts(isNull(), isNull(), isNull(), isNull(), isNull(), eq(20)))
                .thenReturn(new CursorPagedResponse<>(List.of(), null, false, 20));

        mockMvc(null).perform(get("/api/v1/job-posts").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 생성은_201이다() throws Exception {
        when(jobPostService.create(eq(1L), eq(UserRole.USER), any(CreateJobPostRequest.class)))
                .thenReturn(detail());
        CreateJobPostRequest req = new CreateJobPostRequest("멜론기관", "<p>c</p>",
                TherapyArea.SPEECH, EmploymentType.FULL_TIME, Region.SEOUL, "협의", null, null,
                "https://example.com", LocalDate.now().plusDays(5));

        mockMvc(principal(1L, UserRole.USER)).perform(post("/api/v1/job-posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(jobPostService).create(eq(1L), eq(UserRole.USER), any(CreateJobPostRequest.class));
    }

    @Test
    void sourceUrl_형식위반은_400이다() throws Exception {
        CreateJobPostRequest req = new CreateJobPostRequest("멜론기관", "<p>c</p>",
                TherapyArea.SPEECH, EmploymentType.FULL_TIME, Region.SEOUL, "협의", null, null,
                "not-a-url", LocalDate.now().plusDays(5));

        mockMvc(principal(1L, UserRole.USER)).perform(post("/api/v1/job-posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 필수필드_누락은_400이다() throws Exception {
        CreateJobPostRequest req = new CreateJobPostRequest(null, null,
                null, null, null, null, null, null, null, null);

        mockMvc(principal(1L, UserRole.USER)).perform(post("/api/v1/job-posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
