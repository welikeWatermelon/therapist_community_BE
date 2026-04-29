package com.therapyCommunity_Vol1.backend.comment.service;

import com.therapyCommunity_Vol1.backend.autocomment.config.AiCommentProperties;
import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.comment.dto.CommentResponse;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommentThreadAssemblerTest {

    private AiCommentProperties aiCommentProperties;
    private CommentThreadAssembler assembler;

    private User author;
    private TherapyPost post;

    @BeforeEach
    void setUp() {
        aiCommentProperties = mock(AiCommentProperties.class);
        when(aiCommentProperties.getAiUserEmail()).thenReturn("ai-comment@system.local");
        assembler = new CommentThreadAssembler(aiCommentProperties);

        author = User.builder()
                .id(1L).email("t@t.com").nickname("tester").role(UserRole.THERAPIST)
                .build();
        post = TherapyPost.create("<p>본문</p>", TherapyArea.SPEECH, Visibility.PUBLIC, author);
        ReflectionTestUtils.setField(post, "id", 100L);
    }

    private TherapyPostComment root(long id, String content) {
        TherapyPostComment c = TherapyPostComment.createRoot(post, author, content);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private TherapyPostComment reply(long id, TherapyPostComment parent, String content) {
        TherapyPostComment c = TherapyPostComment.createReply(post, author, parent, content);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private TherapyPostComment softDelete(TherapyPostComment c) {
        c.softDelete();
        return c;
    }

    @Test
    void 활성_root와_활성_reply는_트리로_묶인다() {
        TherapyPostComment r1 = root(1L, "루트1");
        TherapyPostComment p1 = reply(2L, r1, "리플1");

        List<CommentResponse> result = assembler.assemble(List.of(r1, p1), 1L, UserRole.USER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).isDeleted()).isFalse();
        assertThat(result.get(0).getReplies()).hasSize(1);
        assertThat(result.get(0).getReplies().get(0).getId()).isEqualTo(2L);
    }

    @Test
    void 삭제된_root는_placeholder로_유지하고_자식_reply는_여전히_노출된다() {
        // 삭제된 root에 자식 reply가 살아있는 경우 — thread 연속성 위해 root를 placeholder로 유지
        TherapyPostComment r1 = softDelete(root(1L, "루트1"));
        TherapyPostComment p1 = reply(2L, r1, "리플1");

        List<CommentResponse> result = assembler.assemble(List.of(r1, p1), 1L, UserRole.USER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).isDeleted()).isTrue();
        assertThat(result.get(0).getContent()).isEqualTo("삭제된 댓글입니다.");
        // 자식 reply는 그대로 노출 (orphan 방지)
        assertThat(result.get(0).getReplies()).hasSize(1);
        assertThat(result.get(0).getReplies().get(0).getId()).isEqualTo(2L);
    }

    @Test
    void 삭제된_reply는_응답에서_완전히_제외된다() {
        TherapyPostComment r1 = root(1L, "루트1");
        TherapyPostComment p1 = reply(2L, r1, "리플1");
        TherapyPostComment p2 = softDelete(reply(3L, r1, "리플2_삭제됨"));

        List<CommentResponse> result = assembler.assemble(List.of(r1, p1, p2), 1L, UserRole.USER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReplies()).hasSize(1);
        assertThat(result.get(0).getReplies().get(0).getId()).isEqualTo(2L);
    }

    @Test
    void 모든_reply가_삭제되면_root만_빈_replies로_노출된다() {
        TherapyPostComment r1 = root(1L, "루트1");
        TherapyPostComment p1 = softDelete(reply(2L, r1, "리플1_삭제"));
        TherapyPostComment p2 = softDelete(reply(3L, r1, "리플2_삭제"));

        List<CommentResponse> result = assembler.assemble(List.of(r1, p1, p2), 1L, UserRole.USER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReplies()).isEmpty();
    }
}
