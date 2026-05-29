package com.therapyCommunity_Vol1.backend.autocomment.service;

import com.therapyCommunity_Vol1.backend.autocomment.config.AiCommentProperties;
import com.therapyCommunity_Vol1.backend.autocomment.domain.AutoCommentJobStatus;
import com.therapyCommunity_Vol1.backend.autocomment.domain.PostAiCommentJob;
import com.therapyCommunity_Vol1.backend.autocomment.domain.ReviewStatus;
import com.therapyCommunity_Vol1.backend.autocomment.domain.SourceMode;
import com.therapyCommunity_Vol1.backend.autocomment.dto.AiCommentDraftResponse;
import com.therapyCommunity_Vol1.backend.autocomment.repository.PostAiCommentJobRepository;
import com.therapyCommunity_Vol1.backend.comment.dto.CommentResponse;
import com.therapyCommunity_Vol1.backend.comment.service.CommentService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AiCommentReviewServiceTest {

    private PostAiCommentJobRepository jobRepository;
    private UserService userService;
    private CommentService commentService;
    private AiCommentProperties properties;
    private AiCommentReviewService reviewService;

    private User admin;
    private User aiUser;
    private TherapyPost post;
    private PostAiCommentJob job;

    @BeforeEach
    void setUp() {
        jobRepository = mock(PostAiCommentJobRepository.class);
        userService = mock(UserService.class);
        commentService = mock(CommentService.class);
        properties = new AiCommentProperties();
        properties.setAiUserEmail("ai-comment@system.local");

        reviewService = new AiCommentReviewService(
                jobRepository, userService, commentService, properties
        );

        admin = User.builder().id(99L).email("admin@test.com").nickname("관리자").role(UserRole.ADMIN).build();
        aiUser = User.builder().id(100L).email("ai-comment@system.local").nickname("Melonne AI").role(UserRole.ADMIN).build();

        post = TherapyPost.create("<p>질문</p>", TherapyArea.SPEECH, Visibility.PUBLIC, admin);
        ReflectionTestUtils.setField(post, "id", 10L);

        job = PostAiCommentJob.create(post, admin);
        ReflectionTestUtils.setField(job, "id", 1L);
        job.markProcessing();
        job.markSucceeded("초안 댓글입니다.", null, SourceMode.RAG, 0.8);
    }

    @Test
    void approve_성공_댓글생성_review_APPROVED() {
        when(jobRepository.findByPostId(10L)).thenReturn(Optional.of(job));
        when(userService.findById(99L)).thenReturn(admin);
        when(userService.findByEmail("ai-comment@system.local")).thenReturn(aiUser);

        CommentResponse mockComment = new CommentResponse(
                50L, 10L, null, 100L, "Melonne AI", "ADMIN", null, true,
                "초안 댓글입니다.", false, true, true, null, null,
                0L, 0L, 0L, null,
                List.of()
        );
        when(commentService.createComment(eq(100L), eq(UserRole.ADMIN), eq(10L), any())).thenReturn(mockComment);

        AiCommentDraftResponse response = reviewService.approve(10L, 99L);

        assertThat(response.reviewStatus()).isEqualTo("APPROVED");
        verify(commentService).createComment(eq(100L), eq(UserRole.ADMIN), eq(10L), any());
    }

    @Test
    void reject_성공_댓글미생성_review_REJECTED() {
        when(jobRepository.findByPostId(10L)).thenReturn(Optional.of(job));
        when(userService.findById(99L)).thenReturn(admin);

        AiCommentDraftResponse response = reviewService.reject(10L, 99L);

        assertThat(response.reviewStatus()).isEqualTo("REJECTED");
        verifyNoInteractions(commentService);
    }

    @Test
    void 이미_승인된_초안_approve시_400() {
        job.approve(admin, null);
        when(jobRepository.findByPostId(10L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> reviewService.approve(10L, 99L))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void getDraft_존재하지_않으면_404() {
        when(jobRepository.findByPostId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.getDraft(999L))
                .isInstanceOf(CustomException.class);
    }
}
