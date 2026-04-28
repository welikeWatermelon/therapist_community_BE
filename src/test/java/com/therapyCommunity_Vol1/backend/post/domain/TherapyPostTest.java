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
        assertThat(post.getSearchText()).contains("본문");
        assertThat(post.getSearchText()).contains(TherapyArea.SPEECH.getDescription());
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
        assertThat(post.getSearchText()).contains("수정된 본문");
        assertThat(post.getSearchText()).contains(TherapyArea.COGNITIVE.getDescription());
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

    @Test
    void searchText는_content를_100자까지만_포함한다() {
        // given
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        String longContent = "가".repeat(200);

        // when
        TherapyPost post = TherapyPost.create(
                longContent,
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );

        // then — content 100자 + 공백 + therapyArea 설명
        String expected = "가".repeat(100) + " " + TherapyArea.SPEECH.getDescription();
        assertThat(post.getSearchText()).isEqualTo(expected);
    }

    @Test
    void searchText는_content가_정확히_100자이면_100자_전부_포함한다() {
        // given
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        String exactly100 = "가".repeat(100);

        // when
        TherapyPost post = TherapyPost.create(
                exactly100,
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                author
        );

        // then — 100자 전부 포함 + therapyArea
        String expected = exactly100 + " " + TherapyArea.SPEECH.getDescription();
        assertThat(post.getSearchText()).isEqualTo(expected);
    }

    @Test
    void searchText는_content가_빈문자열이면_therapyArea만_포함한다() {
        // given
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        // when
        TherapyPost post = TherapyPost.create(
                "",
                TherapyArea.ART,
                Visibility.PUBLIC,
                author
        );

        // then — 빈 content + trim 후 therapyArea만 남음
        assertThat(post.getSearchText()).isEqualTo(TherapyArea.ART.getDescription());
    }

    @Test
    void searchText는_therapyArea가_null이면_UNSPECIFIED로_폴백된다() {
        // given
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        // when — therapyArea null → UNSPECIFIED로 폴백됨
        TherapyPost post = TherapyPost.create(
                "본문 내용",
                null,
                Visibility.PUBLIC,
                author
        );

        // then — UNSPECIFIED의 description 포함
        assertThat(post.getSearchText()).contains("본문 내용");
        assertThat(post.getSearchText()).contains(TherapyArea.UNSPECIFIED.getDescription());
    }

}