package com.therapyCommunity_Vol1.backend.jobpost.repository;

import com.therapyCommunity_Vol1.backend.jobpost.domain.EmploymentType;
import com.therapyCommunity_Vol1.backend.jobpost.domain.JobPost;
import com.therapyCommunity_Vol1.backend.jobpost.domain.Region;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class JobPostRepositoryCursorTest {

    @Autowired
    private JobPostRepository jobPostRepository;
    @Autowired
    private UserRepository userRepository;

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 16);
    private User author;

    @BeforeEach
    void setUp() {
        author = userRepository.save(User.builder()
                .email("a@test.com").passwordHash("pw").nickname("작성자")
                .role(UserRole.THERAPIST).build());
    }

    private JobPost save(String org, LocalDate deadline, boolean closed,
                         TherapyArea area, Region region, EmploymentType type) {
        JobPost post = JobPost.create(author, org, "<p>c</p>", area, type, region,
                "협의", null, null, "https://example.com", deadline);
        if (closed) post.closeManually();
        return jobPostRepository.save(post);
    }

    @Test
    void OPEN목록은_마감임박순_정렬되고_동일마감일은_id오름차순이다() {
        JobPost a = save("A", TODAY.plusDays(2), false, TherapyArea.SPEECH, Region.SEOUL, EmploymentType.FULL_TIME);
        JobPost b = save("B", TODAY.plusDays(2), false, TherapyArea.SPEECH, Region.SEOUL, EmploymentType.FULL_TIME);
        JobPost c = save("C", TODAY.plusDays(1), false, TherapyArea.SPEECH, Region.SEOUL, EmploymentType.FULL_TIME);

        List<JobPost> result = jobPostRepository.findOpenFeed(
                TODAY, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result).extracting(JobPost::getId)
                .containsExactly(c.getId(), a.getId(), b.getId());
    }

    @Test
    void OPEN목록은_마감지남_조기마감_삭제를_제외한다() {
        save("미래", TODAY.plusDays(1), false, TherapyArea.SPEECH, Region.SEOUL, EmploymentType.FULL_TIME);
        save("지남", TODAY.minusDays(1), false, TherapyArea.SPEECH, Region.SEOUL, EmploymentType.FULL_TIME);
        save("조기마감", TODAY.plusDays(3), true, TherapyArea.SPEECH, Region.SEOUL, EmploymentType.FULL_TIME);
        JobPost deleted = save("삭제", TODAY.plusDays(2), false, TherapyArea.SPEECH, Region.SEOUL, EmploymentType.FULL_TIME);
        deleted.softDelete();
        jobPostRepository.save(deleted);

        List<JobPost> result = jobPostRepository.findOpenFeed(
                TODAY, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result).extracting(JobPost::getOrganizationName).containsExactly("미래");
    }

    @Test
    void OPEN목록_커서로_다음페이지가_중복없이_이어진다() {
        for (int i = 1; i <= 5; i++) {
            save("J" + i, TODAY.plusDays(i), false, TherapyArea.SPEECH, Region.SEOUL, EmploymentType.FULL_TIME);
        }
        List<JobPost> first = jobPostRepository.findOpenFeed(
                TODAY, null, null, null, null, null, PageRequest.of(0, 2));
        assertThat(first).hasSize(2);

        JobPost last = first.get(1);
        List<JobPost> second = jobPostRepository.findOpenFeed(
                TODAY, null, null, null, last.getDeadlineDate(), last.getId(), PageRequest.of(0, 2));

        assertThat(second).extracting(JobPost::getId)
                .doesNotContainAnyElementsOf(first.stream().map(JobPost::getId).toList());
    }

    @Test
    void OPEN목록은_필터로_좁혀진다() {
        save("언어서울", TODAY.plusDays(1), false, TherapyArea.SPEECH, Region.SEOUL, EmploymentType.FULL_TIME);
        save("미술부산", TODAY.plusDays(1), false, TherapyArea.ART, Region.BUSAN, EmploymentType.CONTRACT);

        List<JobPost> result = jobPostRepository.findOpenFeed(
                TODAY, TherapyArea.ART, Region.BUSAN, EmploymentType.CONTRACT, null, null, PageRequest.of(0, 10));

        assertThat(result).extracting(JobPost::getOrganizationName).containsExactly("미술부산");
    }

    @Test
    void CLOSED목록은_마감지남과_조기마감을_최근마감순으로_조회한다() {
        save("미래오픈", TODAY.plusDays(5), false, TherapyArea.SPEECH, Region.SEOUL, EmploymentType.FULL_TIME);
        save("지남1", TODAY.minusDays(1), false, TherapyArea.SPEECH, Region.SEOUL, EmploymentType.FULL_TIME);
        save("지남2", TODAY.minusDays(5), false, TherapyArea.SPEECH, Region.SEOUL, EmploymentType.FULL_TIME);
        save("조기마감", TODAY.plusDays(2), true, TherapyArea.SPEECH, Region.SEOUL, EmploymentType.FULL_TIME);

        List<JobPost> result = jobPostRepository.findClosedFeed(
                TODAY, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result).extracting(JobPost::getOrganizationName)
                .containsExactly("조기마감", "지남1", "지남2");
    }

    @Test
    void findByIdAndDeletedAtIsNull은_삭제된_공고를_못_찾는다() {
        JobPost post = save("삭제대상", TODAY.plusDays(1), false, TherapyArea.SPEECH, Region.SEOUL, EmploymentType.FULL_TIME);
        post.softDelete();
        jobPostRepository.save(post);

        assertThat(jobPostRepository.findByIdAndDeletedAtIsNull(post.getId())).isEmpty();
    }
}
