package com.therapyCommunity_Vol1.backend.scrap.dto;

import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.scrap.domain.TherapyPostScrap;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScrappedPostResponseTest {

    @Test
    void 스크랩목록응답은_html을_제거하고_preview를_만든다() {

        // given
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "<p>" + "a".repeat(250) + "</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                user
        );

        TherapyPostScrap scrap = TherapyPostScrap.create(post, user);

        // when
        ScrappedPostResponse response = ScrappedPostResponse.from(scrap);

        // then
        assertThat(response.getContentPreview()).doesNotContain("<p>");
        assertThat(response.getContentPreview().length()).isEqualTo(200);
    }
}