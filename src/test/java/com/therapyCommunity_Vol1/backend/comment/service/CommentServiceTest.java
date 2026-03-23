package com.therapyCommunity_Vol1.backend.comment.service;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.comment.dto.CommentResponse;
import com.therapyCommunity_Vol1.backend.comment.dto.CreateCommentRequest;
import com.therapyCommunity_Vol1.backend.comment.dto.UpdateCommentRequest;
import com.therapyCommunity_Vol1.backend.comment.repository.TherapyPostCommentRepository;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.post.domain.AgeGroup;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import com.therapyCommunity_Vol1.backend.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommentServiceTest {

    private TherapyPostCommentRepository commentRepository;
    private TherapyPostRepository postRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;
    private CommentThreadAssembler commentThreadAssembler;
    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentRepository = mock(TherapyPostCommentRepository.class);
        postRepository = mock(TherapyPostRepository.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        commentThreadAssembler = new CommentThreadAssembler();
        commentService = new CommentService(commentRepository, postRepository, userRepository, notificationService, commentThreadAssembler);
    }

    @Test
    void 루트_댓글_작성_성공() {

        // given
        Long currentUserId = 1L;

        User author = User.builder()
                .id(currentUserId)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "제목",
                "<p>본문</p>",
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5,
                author
        );
        ReflectionTestUtils.setField(post, "id", 10L);

        CreateCommentRequest request = new CreateCommentRequest("루트 댓글", null);

        TherapyPostComment saved = TherapyPostComment.createRoot(post, author, "루트 댓글");
        ReflectionTestUtils.setField(saved, "id", 100L);

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(author));
        when(postRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(TherapyPostComment.class))).thenReturn(saved);

        // when
        CommentResponse response = commentService.createComment(currentUserId, 10L, request);

        // then
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getParentCommentId()).isNull();
        assertThat(response.getContent()).isEqualTo("루트 댓글");
        assertThat(response.getAuthorId()).isEqualTo(currentUserId);
        assertThat(response.getAuthorRole()).isEqualTo("THERAPIST");
        assertThat(response.isCanEdit()).isTrue();
        assertThat(response.getReplies()).isEmpty();
    }

    @Test
    void 대댓글_작성_성공() {

        // given
        Long currentUserId = 1L;

        User author = User.builder()
                .id(currentUserId)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "제목",
                "<p>본문</p>",
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5,
                author
        );
        ReflectionTestUtils.setField(post, "id", 10L);

        TherapyPostComment parent = TherapyPostComment.createRoot(post, author, "부모 댓글");
        ReflectionTestUtils.setField(parent, "id", 50L);

        CreateCommentRequest request = new CreateCommentRequest("대댓글", 50L);

        TherapyPostComment saved = TherapyPostComment.createReply(post, author, parent, "대댓글");
        ReflectionTestUtils.setField(saved, "id", 101L);

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(author));
        when(postRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(post));
        when(commentRepository.findByIdAndDeletedAtIsNull(50L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(TherapyPostComment.class))).thenReturn(saved);

        // when
        CommentResponse response = commentService.createComment(currentUserId, 10L, request);

        // then
        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getParentCommentId()).isEqualTo(50L);
        assertThat(response.isCanDelete()).isTrue();
    }

    @Test
    void 대댓글의_대댓글은_실패() {

        // given
        Long currentUserId = 1L;

        User author = User.builder()
                .id(currentUserId)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "제목",
                "<p>본문</p>",
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5,
                author
        );
        ReflectionTestUtils.setField(post, "id", 10L);

        TherapyPostComment parent = TherapyPostComment.createRoot(post, author, "부모 댓글");
        ReflectionTestUtils.setField(parent, "id", 50L);

        TherapyPostComment reply = TherapyPostComment.createReply(post, author, parent, "대댓글");
        ReflectionTestUtils.setField(reply, "id", 60L);

        CreateCommentRequest request = new CreateCommentRequest("대댓글의 대댓글", 60L);

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(author));
        when(postRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(post));
        when(commentRepository.findByIdAndDeletedAtIsNull(60L)).thenReturn(Optional.of(reply));

        // when / then
        assertThatThrownBy(() -> commentService.createComment(currentUserId, 10L, request))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void 댓글_수정_실패_작성자아님() {

        // given
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "제목",
                "<p>본문</p>",
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5,
                author
        );

        TherapyPostComment comment = TherapyPostComment.createRoot(post, author, "댓글");
        ReflectionTestUtils.setField(comment, "id", 100L);

        UpdateCommentRequest request = new UpdateCommentRequest("수정 댓글");

        when(commentRepository.findByIdAndDeletedAtIsNull(100L))
                .thenReturn(Optional.of(comment));

        // when / then
        assertThatThrownBy(() ->
                commentService.updateComment(2L, UserRole.THERAPIST, 100L, request)
        ).isInstanceOf(CustomException.class);
    }

    @Test
    void 댓글_soft_delete_성공() {

        // given
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "제목",
                "<p>본문</p>",
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5,
                author
        );

        TherapyPostComment comment = TherapyPostComment.createRoot(post, author, "댓글");
        ReflectionTestUtils.setField(comment, "id", 100L);

        when(commentRepository.findByIdAndDeletedAtIsNull(100L))
                .thenReturn(Optional.of(comment));

        // when
        commentService.deleteComment(1L, UserRole.THERAPIST, 100L);

        // then
        assertThat(comment.isDeleted()).isTrue();
    }

    @Test
    void 댓글_목록_조회_성공() {

        // given
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "제목",
                "<p>본문입니다</p>",
                TherapyArea.COGNITIVE,
                AgeGroup.AGE_6_12,
                author
        );
        ReflectionTestUtils.setField(post, "id", 10L);

        TherapyPostComment comment = TherapyPostComment.createRoot(post, author, "댓글");
        ReflectionTestUtils.setField(comment, "id", 100L);

        when(postRepository.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findByPostIdOrderByCreatedAtAsc(post.getId())).thenReturn(List.of(comment));

        // when
        List<CommentResponse> responses = commentService.getComments(1L, UserRole.THERAPIST, 10L);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getContent()).isEqualTo("댓글");
        assertThat(responses.get(0).isCanEdit()).isTrue();
        assertThat(responses.get(0).getReplies()).isEmpty();
    }

    @Test
    void 댓글_목록_조회시_대댓글을_replies로_조립한다() {
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();
        User replyAuthor = User.builder()
                .id(2L)
                .email("reply@test.com")
                .nickname("replier")
                .role(UserRole.USER)
                .build();

        TherapyPost post = TherapyPost.create(
                "제목",
                "<p>본문입니다</p>",
                TherapyArea.COGNITIVE,
                AgeGroup.AGE_6_12,
                author
        );
        ReflectionTestUtils.setField(post, "id", 10L);

        TherapyPostComment root = TherapyPostComment.createRoot(post, author, "부모 댓글");
        ReflectionTestUtils.setField(root, "id", 100L);
        ReflectionTestUtils.setField(root, "createdAt", java.time.LocalDateTime.of(2026, 3, 16, 10, 0));

        TherapyPostComment reply = TherapyPostComment.createReply(post, replyAuthor, root, "대댓글");
        ReflectionTestUtils.setField(reply, "id", 101L);
        ReflectionTestUtils.setField(reply, "createdAt", java.time.LocalDateTime.of(2026, 3, 16, 10, 5));

        when(postRepository.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findByPostIdOrderByCreatedAtAsc(post.getId())).thenReturn(List.of(root, reply));

        List<CommentResponse> responses = commentService.getComments(1L, UserRole.THERAPIST, 10L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo(100L);
        assertThat(responses.get(0).getReplies()).hasSize(1);
        assertThat(responses.get(0).getReplies().get(0).getId()).isEqualTo(101L);
        assertThat(responses.get(0).getReplies().get(0).getParentCommentId()).isEqualTo(100L);
        assertThat(responses.get(0).getReplies().get(0).isCanEdit()).isFalse();
    }

    @Test
    void 삭제된_부모댓글도_자리를_유지한채_replies를_보여준다() {
        User author = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();
        User replyAuthor = User.builder()
                .id(2L)
                .email("reply@test.com")
                .nickname("replier")
                .role(UserRole.USER)
                .build();

        TherapyPost post = TherapyPost.create(
                "제목",
                "<p>본문입니다</p>",
                TherapyArea.COGNITIVE,
                AgeGroup.AGE_6_12,
                author
        );
        ReflectionTestUtils.setField(post, "id", 10L);

        TherapyPostComment root = TherapyPostComment.createRoot(post, author, "부모 댓글");
        ReflectionTestUtils.setField(root, "id", 100L);
        root.softDelete();

        TherapyPostComment reply = TherapyPostComment.createReply(post, replyAuthor, root, "대댓글");
        ReflectionTestUtils.setField(reply, "id", 101L);

        when(postRepository.findByIdAndDeletedAtIsNull(post.getId())).thenReturn(Optional.of(post));
        when(commentRepository.findByPostIdOrderByCreatedAtAsc(post.getId())).thenReturn(List.of(root, reply));

        List<CommentResponse> responses = commentService.getComments(1L, UserRole.THERAPIST, 10L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).isDeleted()).isTrue();
        assertThat(responses.get(0).getContent()).isEqualTo("삭제된 댓글입니다.");
        assertThat(responses.get(0).getReplies()).hasSize(1);
    }
}
