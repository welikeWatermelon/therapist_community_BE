package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TherapyPostSummaryResponseTest {

    @Test
    void 목록응답은_html을_제거하고_200자_preview만_만든다() {
        // given
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();
        String content = "<p>" + "a".repeat(250) + "</p>";

        TherapyPost post = TherapyPost.create(
                content,
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );

        //when
        TherapyPostSummaryResponse response = TherapyPostSummaryResponse.from(post);

        // then
        assertThat(response.getContentPreview()).doesNotContain("<p>");
        assertThat(response.getContentPreview().length()).isEqualTo(200);
    }

}