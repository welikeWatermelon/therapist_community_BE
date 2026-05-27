package com.therapyCommunity_Vol1.backend.post.repository;

import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPostImage;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class TherapyPostImageRepositoryTest {

    @Autowired
    private TherapyPostImageRepository imageRepository;

    @Autowired
    private TherapyPostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private TherapyPost post;

    @BeforeEach
    void setUp() {
        User author = userRepository.save(
                User.builder()
                        .email("t@test.com").passwordHash("pw").nickname("t").role(UserRole.THERAPIST)
                        .build());
        post = postRepository.save(
                TherapyPost.create("<p>c</p>", TherapyArea.SPEECH, Visibility.PUBLIC, author));
    }

    @Test
    void storedPath_중복_INSERT_는_유니크_제약으로_거부된다() {
        imageRepository.saveAndFlush(
                TherapyPostImage.create(post, "post-images/dup.jpg", "a.jpg", "image/jpeg", 100L, 0));

        assertThatThrownBy(() -> imageRepository.saveAndFlush(
                TherapyPostImage.create(post, "post-images/dup.jpg", "b.jpg", "image/jpeg", 200L, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByStoredPath_로_조회된다() {
        imageRepository.saveAndFlush(
                TherapyPostImage.create(post, "post-images/x.jpg", "a.jpg", "image/jpeg", 100L, 0));

        assertThat(imageRepository.findByStoredPath("post-images/x.jpg")).isPresent();
        assertThat(imageRepository.findByStoredPath("post-images/none.jpg")).isEmpty();
    }
}
