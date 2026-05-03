package com.therapyCommunity_Vol1.backend.comment.dto;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CommentResponseTest {

    @Test
    void 삭제된_댓글은_placeholder를_반환한다() {

        // given
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );
        ReflectionTestUtils.setField(post, "id", 1L);

        TherapyPostComment comment = TherapyPostComment.createRoot(
                post,
                author,
                "원본 댓글"
        );
        ReflectionTestUtils.setField(comment, "id", 10L);
        ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(comment, "updatedAt", LocalDateTime.now());

        comment.softDelete();

        // when
        CommentResponse response = CommentResponse.from(comment, 2L, UserRole.THERAPIST, null, null,
                com.therapyCommunity_Vol1.backend.comment.dto.CommentReactionAggregate.empty());

        // then
        assertThat(response.isDeleted()).isTrue();
        assertThat(response.getContent()).isEqualTo("삭제된 댓글입니다.");
        assertThat(response.isCanEdit()).isFalse();
        assertThat(response.isCanDelete()).isFalse();
        assertThat(response.getReplies()).isEmpty();
    }
}
