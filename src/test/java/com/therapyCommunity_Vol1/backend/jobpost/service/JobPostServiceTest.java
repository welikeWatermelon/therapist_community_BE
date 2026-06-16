package com.therapyCommunity_Vol1.backend.jobpost.service;

import com.therapyCommunity_Vol1.backend.global.common.CursorPagedResponse;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.global.security.ResourceAccessValidator;
import com.therapyCommunity_Vol1.backend.jobpost.domain.EmploymentType;
import com.therapyCommunity_Vol1.backend.jobpost.domain.JobPost;
import com.therapyCommunity_Vol1.backend.jobpost.domain.JobPostStatus;
import com.therapyCommunity_Vol1.backend.jobpost.domain.Region;
import com.therapyCommunity_Vol1.backend.jobpost.dto.CreateJobPostRequest;
import com.therapyCommunity_Vol1.backend.jobpost.dto.JobPostDetailResponse;
import com.therapyCommunity_Vol1.backend.jobpost.dto.JobPostSummaryResponse;
import com.therapyCommunity_Vol1.backend.jobpost.dto.UpdateJobPostRequest;
import com.therapyCommunity_Vol1.backend.jobpost.repository.JobPostRepository;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobPostServiceTest {

    private JobPostRepository jobPostRepository;
    private UserService userService;
    private ResourceAccessValidator resourceAccessValidator;
    private JobPostService jobPostService;

    private static final LocalDate FUTURE = LocalDate.now().plusDays(10);

    @BeforeEach
    void setUp() {
        jobPostRepository = mock(JobPostRepository.class);
        userService = mock(UserService.class);
        resourceAccessValidator = new ResourceAccessValidator();
        jobPostService = new JobPostService(jobPostRepository, userService, resourceAccessValidator);
    }

    private User user(Long id, UserRole role) {
        return User.builder().id(id).email("u" + id + "@test.com")
                .nickname("user" + id).role(role).build();
    }

    private JobPost jobPost(Long id, User author) {
        JobPost post = JobPost.create(author, "멜론기관", "<p>c</p>",
                TherapyArea.SPEECH, EmploymentType.FULL_TIME, Region.SEOUL,
                "협의", null, null, "https://example.com", FUTURE);
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private CreateJobPostRequest createRequest() {
        return new CreateJobPostRequest("멜론기관", "<p>c</p>", TherapyArea.SPEECH,
                EmploymentType.FULL_TIME, Region.SEOUL, "협의", null, null,
                "https://example.com", FUTURE);
    }

    private UpdateJobPostRequest updateRequest() {
        return new UpdateJobPostRequest("새기관", "<p>u</p>", TherapyArea.ART,
                EmploymentType.CONTRACT, Region.BUSAN, "3000만원", "자격", "우대",
                "https://example.com/new", FUTURE.plusDays(5));
    }

    @Test
    void 생성하면_저장하고_상세를_반환한다() {
        User author = user(1L, UserRole.USER);
        when(userService.findById(1L)).thenReturn(author);
        when(jobPostRepository.save(any(JobPost.class))).thenAnswer(inv -> {
            JobPost p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 100L);
            return p;
        });

        JobPostDetailResponse res = jobPostService.create(1L, UserRole.USER, createRequest());

        assertThat(res.getId()).isEqualTo(100L);
        assertThat(res.getOrganizationName()).isEqualTo("멜론기관");
        verify(jobPostRepository).save(any(JobPost.class));
    }

    @Test
    void 작성자는_수정할_수_있다() {
        User author = user(1L, UserRole.USER);
        when(jobPostRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(jobPost(100L, author)));

        JobPostDetailResponse res = jobPostService.update(1L, UserRole.USER, 100L, updateRequest());

        assertThat(res.getOrganizationName()).isEqualTo("새기관");
        assertThat(res.getRegion()).isEqualTo(Region.BUSAN);
    }

    @Test
    void admin은_타인_공고를_수정할_수_있다() {
        User author = user(1L, UserRole.USER);
        when(jobPostRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(jobPost(100L, author)));

        JobPostDetailResponse res = jobPostService.update(999L, UserRole.ADMIN, 100L, updateRequest());

        assertThat(res.getOrganizationName()).isEqualTo("새기관");
    }

    @Test
    void 타인은_수정할_수_없다() {
        User author = user(1L, UserRole.USER);
        when(jobPostRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(jobPost(100L, author)));

        assertThatThrownBy(() -> jobPostService.update(2L, UserRole.USER, 100L, updateRequest()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.JOB_POST_ACCESS_DENIED);
    }

    @Test
    void 작성자는_삭제할_수_있다() {
        User author = user(1L, UserRole.USER);
        JobPost post = jobPost(100L, author);
        when(jobPostRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(post));

        jobPostService.delete(1L, UserRole.USER, 100L);

        assertThat(post.isDeleted()).isTrue();
    }

    @Test
    void 작성자는_조기마감할_수_있고_타인은_못한다() {
        User author = user(1L, UserRole.USER);
        JobPost post = jobPost(100L, author);
        when(jobPostRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(post));

        jobPostService.close(1L, UserRole.USER, 100L);
        assertThat(post.isClosedManually()).isTrue();

        when(jobPostRepository.findByIdAndDeletedAtIsNull(101L)).thenReturn(Optional.of(jobPost(101L, author)));
        assertThatThrownBy(() -> jobPostService.close(2L, UserRole.USER, 101L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.JOB_POST_ACCESS_DENIED);
    }

    @Test
    void 삭제된_공고_상세조회는_NOT_FOUND다() {
        when(jobPostRepository.findByIdAndDeletedAtIsNull(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobPostService.getDetail(404L, null, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.JOB_POST_NOT_FOUND);
    }

    @Test
    void 마감_공고도_상세는_조회된다() {
        User author = user(1L, UserRole.USER);
        JobPost post = jobPost(100L, author);
        post.closeManually();
        when(jobPostRepository.findByIdAndDeletedAtIsNull(100L)).thenReturn(Optional.of(post));

        JobPostDetailResponse res = jobPostService.getDetail(100L, null, null);

        assertThat(res.getStatus()).isEqualTo(JobPostStatus.CLOSED);
        assertThat(res.isCanEdit()).isFalse();
    }

    @Test
    void 목록은_status_기본값이_OPEN이고_OPEN쿼리에_위임한다() {
        when(jobPostRepository.findOpenFeed(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        CursorPagedResponse<JobPostSummaryResponse> res =
                jobPostService.getJobPosts(null, null, null, null, null, 20);

        assertThat(res.getItems()).isEmpty();
        verify(jobPostRepository).findOpenFeed(any(), any(), any(), any(), any(), any(), any(Pageable.class));
        verify(jobPostRepository, never()).findClosedFeed(any(), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void status_CLOSED면_CLOSED쿼리에_위임한다() {
        when(jobPostRepository.findClosedFeed(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        jobPostService.getJobPosts(JobPostStatus.CLOSED, null, null, null, null, 20);

        verify(jobPostRepository).findClosedFeed(any(), any(), any(), any(), any(), any(), any(Pageable.class));
    }
}
