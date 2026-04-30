package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TherapyPostSummaryResponseTest {

    private TherapyPost privatePost(String content) {
        User author = User.builder()
                .id(1L).email("t@t.com").nickname("tester").role(UserRole.THERAPIST).build();
        return TherapyPost.create(content, TherapyArea.SPEECH, Visibility.PRIVATE, author);
    }

    private TherapyPost publicPost(String content) {
        User author = User.builder()
                .id(1L).email("t@t.com").nickname("tester").role(UserRole.THERAPIST).build();
        return TherapyPost.create(content, TherapyArea.SPEECH, Visibility.PUBLIC, author);
    }

    @Test
    void PRIVATE_게시글은_canViewPrivate_false면_accessLocked_true_본문_마스킹() {
        TherapyPost post = privatePost("<p>비밀 내용</p>");

        TherapyPostSummaryResponse r = TherapyPostSummaryResponse.from(post, 0L, 0L, false, false);

        assertThat(r.isAccessLocked()).isTrue();
        assertThat(r.getContentPreview()).isEqualTo("비공개 글입니다");
        // 메타데이터는 정상 노출 (인증 유도 hook)
        assertThat(r.getAuthorNickname()).isEqualTo("tester");
        assertThat(r.getTherapyArea()).isEqualTo(TherapyArea.SPEECH);
        assertThat(r.getVisibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void PRIVATE_게시글은_canViewPrivate_true면_accessLocked_false_본문_정상_노출() {
        TherapyPost post = privatePost("<p>치료사 자료 본문</p>");

        TherapyPostSummaryResponse r = TherapyPostSummaryResponse.from(post, 0L, 0L, false, true);

        assertThat(r.isAccessLocked()).isFalse();
        assertThat(r.getContentPreview()).isEqualTo("치료사 자료 본문");
    }

    @Test
    void PUBLIC_게시글은_canViewPrivate과_무관하게_accessLocked_false() {
        TherapyPost post = publicPost("<p>공개 내용</p>");

        TherapyPostSummaryResponse rUser = TherapyPostSummaryResponse.from(post, 0L, 0L, false, false);
        TherapyPostSummaryResponse rTherapist = TherapyPostSummaryResponse.from(post, 0L, 0L, false, true);

        assertThat(rUser.isAccessLocked()).isFalse();
        assertThat(rTherapist.isAccessLocked()).isFalse();
        assertThat(rUser.getContentPreview()).isEqualTo("공개 내용");
        assertThat(rTherapist.getContentPreview()).isEqualTo("공개 내용");
    }

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
        TherapyPostSummaryResponse response = TherapyPostSummaryResponse.from(post, false);

        // then
        assertThat(response.getContentPreview()).doesNotContain("<p>");
        assertThat(response.getContentPreview().length()).isEqualTo(200);
    }

}