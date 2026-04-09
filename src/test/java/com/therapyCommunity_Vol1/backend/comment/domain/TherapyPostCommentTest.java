package com.therapyCommunity_Vol1.backend.comment.domain;

import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TherapyPostCommentTest {

    @Test
    void 루트_댓글을_생성할_수_있다() {

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

        // when
        TherapyPostComment comment = TherapyPostComment.createRoot(
                post,
                author,
                "루트 댓글"
        );

        // then
        assertThat(comment.getPost()).isEqualTo(post);
        assertThat(comment.getAuthor()).isEqualTo(author);
        assertThat(comment.getParentComment()).isNull();
        assertThat(comment.isReply()).isFalse();
    }

    @Test
    void 대댓글을_생성할_수_있다() {

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

        TherapyPostComment parent = TherapyPostComment.createRoot(
                post,
                author,
                "부모 댓글"
        );

        // when
        TherapyPostComment reply = TherapyPostComment.createReply(
                post,
                author,
                parent,
                "대댓글"
        );

        // then
        assertThat(reply.getParentComment()).isEqualTo(parent);
        assertThat(reply.isReply()).isTrue();
    }

    @Test
    void 댓글을_soft_delete_할_수_있다() {

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

        TherapyPostComment comment = TherapyPostComment.createRoot(
                post,
                author,
                "댓글"
        );

        // when
        comment.softDelete();

        // then
        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.getDeletedAt()).isNotNull();
    }
}