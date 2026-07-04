package com.therapyCommunity_Vol1.backend.jobpost.domain;

import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostTest {

    private User author(Long id) {
        return User.builder()
                .id(id).email("u" + id + "@test.com").nickname("user" + id)
                .role(UserRole.THERAPIST).build();
    }

    private JobPost jobPost(User author, LocalDate deadline) {
        return JobPost.create(author, "멜론기관", "<p>공고</p>",
                TherapyArea.SPEECH, EmploymentType.FULL_TIME, Region.SEOUL,
                "협의", "자격", "우대", "https://example.com/job", deadline);
    }

    @Test
    void create로_생성하면_필드가_매핑되고_조기마감은_false다() {
        JobPost post = jobPost(author(1L), LocalDate.of(2026, 12, 31));

        assertThat(post.getOrganizationName()).isEqualTo("멜론기관");
        assertThat(post.getTherapyArea()).isEqualTo(TherapyArea.SPEECH);
        assertThat(post.getEmploymentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(post.getRegion()).isEqualTo(Region.SEOUL);
        assertThat(post.getSourceUrl()).isEqualTo("https://example.com/job");
        assertThat(post.isClosedManually()).isFalse();
        assertThat(post.isDeleted()).isFalse();
    }

    @Test
    void 마감일이_오늘이면_OPEN이다() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        JobPost post = jobPost(author(1L), today);

        assertThat(post.deriveStatus(today)).isEqualTo(JobPostStatus.OPEN);
    }

    @Test
    void 마감일이_지났으면_CLOSED다() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        JobPost post = jobPost(author(1L), today.minusDays(1));

        assertThat(post.deriveStatus(today)).isEqualTo(JobPostStatus.CLOSED);
    }

    @Test
    void 조기마감하면_마감일이_미래여도_CLOSED다() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        JobPost post = jobPost(author(1L), today.plusDays(10));

        post.closeManually();

        assertThat(post.isClosedManually()).isTrue();
        assertThat(post.deriveStatus(today)).isEqualTo(JobPostStatus.CLOSED);
    }

    @Test
    void dDay는_오늘부터_마감일까지_일수다() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        assertThat(jobPost(author(1L), today.plusDays(5)).dDay(today)).isEqualTo(5);
        assertThat(jobPost(author(1L), today).dDay(today)).isEqualTo(0);
        assertThat(jobPost(author(1L), today.minusDays(3)).dDay(today)).isEqualTo(-3);
    }

    @Test
    void 마감일이_상시모집_sentinel이면_alwaysOpen이고_항상_OPEN이다() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        JobPost post = jobPost(author(1L), JobPost.ALWAYS_OPEN_DEADLINE);

        assertThat(post.isAlwaysOpen()).isTrue();
        assertThat(post.deriveStatus(today)).isEqualTo(JobPostStatus.OPEN);
    }

    @Test
    void 일반_마감일이면_alwaysOpen이_아니다() {
        JobPost post = jobPost(author(1L), LocalDate.of(2026, 12, 31));

        assertThat(post.isAlwaysOpen()).isFalse();
    }

    @Test
    void 상시모집이어도_조기마감하면_CLOSED다() {
        LocalDate today = LocalDate.of(2026, 6, 16);
        JobPost post = jobPost(author(1L), JobPost.ALWAYS_OPEN_DEADLINE);

        post.closeManually();

        assertThat(post.deriveStatus(today)).isEqualTo(JobPostStatus.CLOSED);
    }

    @Test
    void isAuthor는_작성자id와_일치할때만_true다() {
        JobPost post = jobPost(author(7L), LocalDate.of(2026, 12, 31));

        assertThat(post.isAuthor(7L)).isTrue();
        assertThat(post.isAuthor(8L)).isFalse();
    }

    @Test
    void softDelete하면_isDeleted가_true다() {
        JobPost post = jobPost(author(1L), LocalDate.of(2026, 12, 31));

        post.softDelete();

        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getDeletedAt()).isNotNull();
    }

    @Test
    void update하면_수정필드가_갱신된다() {
        JobPost post = jobPost(author(1L), LocalDate.of(2026, 12, 31));

        post.update("새기관", "<p>수정</p>", TherapyArea.ART, EmploymentType.CONTRACT,
                Region.BUSAN, "3000만원", "새자격", "새우대",
                "https://example.com/new", LocalDate.of(2027, 1, 31));

        assertThat(post.getOrganizationName()).isEqualTo("새기관");
        assertThat(post.getRegion()).isEqualTo(Region.BUSAN);
        assertThat(post.getDeadlineDate()).isEqualTo(LocalDate.of(2027, 1, 31));
    }
}
