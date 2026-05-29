package com.therapyCommunity_Vol1.backend.post.repository;

import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class TherapyPostRepositoryCursorTest {

    @Autowired
    private TherapyPostRepository therapyPostRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User author;

    @BeforeEach
    void setUp() {
        author = userRepository.save(
                User.builder()
                        .email("test@test.com")
                        .passwordHash("password")
                        .nickname("tester")
                        .role(UserRole.THERAPIST)
                        .build()
        );
    }

    @Test
    void 커서_피드_최신순_정렬_및_tie_break() {
        LocalDateTime time1 = LocalDateTime.of(2026, 4, 9, 12, 0, 0);
        LocalDateTime time2 = LocalDateTime.of(2026, 4, 9, 11, 0, 0);

        TherapyPost postA = createPost("글A", Visibility.PUBLIC, time1);
        TherapyPost postB = createPost("글B", Visibility.PUBLIC, time1);
        TherapyPost postC = createPost("글C", Visibility.PUBLIC, time2);

        List<TherapyPost> result = therapyPostRepository.findFeedLatest(List.of(Visibility.PUBLIC, Visibility.PRIVATE), null, PageRequest.of(0, 10));

        assertThat(result).hasSize(3);
        // 같은 createdAt일 때 id DESC로 tie-break
        assertThat(result.get(0).getId()).isGreaterThan(result.get(1).getId());
        // 다른 createdAt은 DESC 정렬
        assertThat(result.get(2).getId()).isEqualTo(postC.getId());
    }

    @Test
    void 커서_피드_다음페이지_중복없이_이어짐() {
        LocalDateTime baseTime = LocalDateTime.of(2026, 4, 9, 12, 0, 0);

        for (int i = 0; i < 5; i++) {
            createPost("글" + i, Visibility.PUBLIC, baseTime.minusMinutes(i));
        }

        // 첫 페이지: 2개 + 1 (hasNext 판단용)
        List<TherapyPost> firstPage = therapyPostRepository.findFeedLatest(List.of(Visibility.PUBLIC, Visibility.PRIVATE), null, PageRequest.of(0, 3));
        assertThat(firstPage).hasSize(3);

        // 두 번째 페이지: 첫 페이지 마지막(2번째) 항목 기준 커서
        TherapyPost lastOfFirst = firstPage.get(1); // size=2일 때 마지막
        List<TherapyPost> secondPage = therapyPostRepository.findFeedLatest(
                List.of(Visibility.PUBLIC, Visibility.PRIVATE), null, lastOfFirst.getCreatedAt(), lastOfFirst.getId(), PageRequest.of(0, 3));

        // 중복 없이 이어져야 함
        List<Long> firstIds = firstPage.subList(0, 2).stream().map(TherapyPost::getId).toList();
        List<Long> secondIds = secondPage.stream().map(TherapyPost::getId).toList();
        assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);
    }

    @Test
    void 커서_피드_visibility_필터_PUBLIC_PRIVATE만_조회() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 9, 12, 0, 0);

        createPost("공개글", Visibility.PUBLIC, now);
        createPost("비공개글", Visibility.PRIVATE, now.minusSeconds(1));
        createPost("팔로워전용", Visibility.FOLLOWERS_ONLY, now.minusSeconds(2));
        createPost("공개글2", Visibility.PUBLIC, now.minusSeconds(3));

        List<TherapyPost> result = therapyPostRepository.findFeedLatest(
                List.of(Visibility.PUBLIC, Visibility.PRIVATE), null, PageRequest.of(0, 10));

        assertThat(result).hasSize(3);
        assertThat(result).noneMatch(p -> p.getVisibility() == Visibility.FOLLOWERS_ONLY);
    }

    @Test
    void 커서_피드_인기순_정렬_및_tie_break() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 9, 12, 0, 0);

        TherapyPost postA = createPost("글A", Visibility.PUBLIC, now);
        TherapyPost postB = createPost("글B", Visibility.PUBLIC, now.minusSeconds(1));
        TherapyPost postC = createPost("글C", Visibility.PUBLIC, now.minusSeconds(2));

        // postB에 높은 점수, postA와 postC는 같은 점수
        setPopularityScore(postB.getId(), 999L);
        setPopularityScore(postA.getId(), 500L);
        setPopularityScore(postC.getId(), 500L);

        List<TherapyPost> result = therapyPostRepository.findFeedPopular(List.of(Visibility.PUBLIC, Visibility.PRIVATE), null, PageRequest.of(0, 10));

        assertThat(result).hasSize(3);
        // 가장 높은 점수가 먼저
        assertThat(result.get(0).getId()).isEqualTo(postB.getId());
        // 같은 점수일 때 id DESC로 tie-break
        assertThat(result.get(1).getId()).isGreaterThan(result.get(2).getId());
    }

    private void setPopularityScore(Long postId, Long score) {
        entityManager.createNativeQuery("UPDATE therapy_posts SET popularity_score = :score WHERE id = :id")
                .setParameter("score", score)
                .setParameter("id", postId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private TherapyPost createPost(String content, Visibility visibility, LocalDateTime createdAt) {
        TherapyPost post = TherapyPost.create(content, TherapyArea.SPEECH, visibility, author);
        TherapyPost saved = therapyPostRepository.save(post);
        entityManager.flush();
        // @PrePersist가 세팅한 createdAt을 테스트용으로 덮어쓰기
        entityManager.createNativeQuery("UPDATE therapy_posts SET created_at = :ca WHERE id = :id")
                .setParameter("ca", createdAt)
                .setParameter("id", saved.getId())
                .executeUpdate();
        entityManager.clear();
        return therapyPostRepository.findById(saved.getId()).orElseThrow();
    }
}
