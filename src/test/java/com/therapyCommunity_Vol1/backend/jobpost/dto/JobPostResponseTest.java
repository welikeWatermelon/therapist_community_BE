package com.therapyCommunity_Vol1.backend.jobpost.dto;

import com.therapyCommunity_Vol1.backend.jobpost.domain.EmploymentType;
import com.therapyCommunity_Vol1.backend.jobpost.domain.JobPost;
import com.therapyCommunity_Vol1.backend.jobpost.domain.JobPostStatus;
import com.therapyCommunity_Vol1.backend.jobpost.domain.Region;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostResponseTest {

    private JobPost jobPost(Long authorId, LocalDate deadline) {
        User author = User.builder()
                .id(authorId).email("a@test.com").nickname("작성자").role(UserRole.THERAPIST).build();
        JobPost post = JobPost.create(author, "멜론기관", "<p>공고</p>",
                TherapyArea.SPEECH, EmploymentType.FULL_TIME, Region.SEOUL,
                "협의", "자격", "우대", "https://example.com/job", deadline);
        ReflectionTestUtils.setField(post, "id", 100L);
        return post;
    }

    @Test
    void summary는_title을_파생하고_dDay와_status를_담는다() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        JobPostSummaryResponse res = JobPostSummaryResponse.from(jobPost(1L, today.plusDays(5)), today);

        assertThat(res.getId()).isEqualTo(100L);
        assertThat(res.getTitle()).isEqualTo("멜론기관 채용공고");
        assertThat(res.getDeadlineDate()).isEqualTo(today.plusDays(5));
        assertThat(res.getDDay()).isEqualTo(5);
        assertThat(res.getStatus()).isEqualTo(JobPostStatus.OPEN);
        assertThat(res.getTherapyAreaLabel()).isEqualTo("언어치료");
        assertThat(res.getRegionLabel()).isEqualTo("서울");
        assertThat(res.getEmploymentTypeLabel()).isEqualTo("정규직");
    }

    @Test
    void detail_canEdit는_작성자면_true다() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        JobPostDetailResponse res = JobPostDetailResponse.from(jobPost(1L, today.plusDays(5)), today, 1L, UserRole.USER);

        assertThat(res.isCanEdit()).isTrue();
        assertThat(res.isCanClose()).isTrue();
        assertThat(res.getContent()).isEqualTo("<p>공고</p>");
        assertThat(res.getAuthorNickname()).isEqualTo("작성자");
    }

    @Test
    void detail_canClose는_이미_마감된_공고면_작성자여도_false다() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        JobPost post = jobPost(1L, today.plusDays(5));
        post.closeManually();

        JobPostDetailResponse res = JobPostDetailResponse.from(post, today, 1L, UserRole.USER);

        assertThat(res.isCanEdit()).isTrue();
        assertThat(res.isCanClose()).isFalse();
    }

    @Test
    void detail_canEdit는_admin이면_true다() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        JobPostDetailResponse res = JobPostDetailResponse.from(jobPost(1L, today.plusDays(5)), today, 999L, UserRole.ADMIN);

        assertThat(res.isCanEdit()).isTrue();
    }

    @Test
    void detail_canEdit는_타인이거나_비회원이면_false다() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        JobPost post = jobPost(1L, today.plusDays(5));

        assertThat(JobPostDetailResponse.from(post, today, 2L, UserRole.USER).isCanEdit()).isFalse();
        assertThat(JobPostDetailResponse.from(post, today, null, null).isCanEdit()).isFalse();
    }
}
