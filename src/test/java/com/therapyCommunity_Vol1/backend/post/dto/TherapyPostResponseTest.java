package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.AgeGroup;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TherapyPostResponseTest {

    @Test
    void contentPreview_200자_제한() {
        // given
        String longContent = "a".repeat(300);

        User author = User.builder()
                .email("a@a.com")
                .nickname("tester")
                .role(UserRole.USER)
                .build();

        TherapyPost post = new TherapyPost(
                "title",
                longContent,
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5,
                author
        );

        // when
        TherapyPostResponse response = new TherapyPostResponse(post);

        // then
        assertThat(response.getContentPreview().length()).isEqualTo(200);

    }
}