package com.therapyCommunity_Vol1.backend.post.domain;

import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TherapyPostTest {

    @Test
    void 게시글을_생성할_수_있다() {
        // given
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        //when
        TherapyPost post = TherapyPost.create(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );

        // then
        assertThat(post.getContent()).isEqualTo("<p>본문</p>");
        assertThat(post.getTherapyArea()).isEqualTo(TherapyArea.SPEECH);
        assertThat(post.getVisibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(post.getAuthor()).isEqualTo(author);
        assertThat(post.getViewCount()).isEqualTo(0L);
        assertThat(post.isDeleted()).isFalse();
    }

    @Test
    void 조회수를_증가시킬_수_있다() {
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
        post.increaseViewCount();
        post.increaseViewCount();

        // then
        assertThat(post.getViewCount()).isEqualTo(2L);
    }

    @Test
    void 게시글을_수정할_수_있다() {
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

        //when
        post.update(
                "<p>수정된 본문</p>",
                TherapyArea.COGNITIVE,
                Visibility.PRIVATE
        );

        // then
        assertThat(post.getContent()).isEqualTo("<p>수정된 본문</p>");
        assertThat(post.getTherapyArea()).isEqualTo(TherapyArea.COGNITIVE);
        assertThat(post.getVisibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void 게시글을_soft_delete_할_수_있다() {
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
        post.softDelete();

        // then
        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getDeletedAt()).isNotNull();
    }

}