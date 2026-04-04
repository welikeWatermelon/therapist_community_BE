package com.therapyCommunity_Vol1.backend.scrap.domain;

import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TherapyPostScrapTest {

    @Test
    void 스크랩을_생성할_수_있다() {

        // given
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                user
        );

        // when
        TherapyPostScrap scrap = TherapyPostScrap.create(post, user);

        // then
        assertThat(scrap.getPost()).isEqualTo(post);
        assertThat(scrap.getUser()).isEqualTo(user);
    }
}