package com.therapyCommunity_Vol1.backend.reaction.service;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.comment.repository.TherapyPostCommentRepository;
import com.therapyCommunity_Vol1.backend.post.domain.AgeGroup;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.reaction.domain.CommentReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostCommentReaction;
import com.therapyCommunity_Vol1.backend.reaction.dto.CommentReactionStatusResponse;
import com.therapyCommunity_Vol1.backend.reaction.dto.ToggleCommentReactionRequest;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostCommentReactionRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CommentReactionServiceTest {

    private TherapyPostCommentReactionRepository commentReactionRepository;
    private TherapyPostCommentRepository commentRepository;
    private UserRepository userRepository;
    private CommentReactionService commentReactionService;

    @BeforeEach
    void setUp() {
        commentReactionRepository = mock(TherapyPostCommentReactionRepository.class);
        commentRepository = mock(TherapyPostCommentRepository.class);
        userRepository = mock(UserRepository.class);
        commentReactionService = new CommentReactionService(
                commentRepository,
                userRepository,
                commentReactionRepository
        );
    }

    @Test
    void 댓글_좋아요_최초등록() {

        // given
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5,
                user
        );

        TherapyPostComment comment = TherapyPostComment.createRoot(post, user, "댓글");

        ToggleCommentReactionRequest request =
                new ToggleCommentReactionRequest(CommentReactionType.LIKE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commentRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(comment));
        when(commentReactionRepository.findByCommentIdAndUserId(20L, 1L)).thenReturn(Optional.empty());

        when(commentReactionRepository.countByCommentIdAndReactionType(20L, CommentReactionType.LIKE)).thenReturn(1L);
        when(commentReactionRepository.countByCommentIdAndReactionType(20L, CommentReactionType.DISLIKE)).thenReturn(0L);

        TherapyPostCommentReaction saved = TherapyPostCommentReaction.create(comment, user, CommentReactionType.LIKE);
        when(commentReactionRepository.findByCommentIdAndUserId(20L, 1L)).thenReturn(Optional.empty(), Optional.of(saved));

        // when
        CommentReactionStatusResponse response = commentReactionService.toggleReaction(1L, 20L, request);

        // then
        verify(commentReactionRepository).save(any(TherapyPostCommentReaction.class));
        assertThat(response.getLikeCount()).isEqualTo(1L);
        assertThat(response.getMyReactionType()).isEqualTo(CommentReactionType.LIKE);
    }

    @Test
    void 댓글_다른반응으로_교체() {

        // given
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                AgeGroup.AGE_3_5,
                user
        );

        TherapyPostComment comment = TherapyPostComment.createRoot(post, user, "댓글");
        TherapyPostCommentReaction existing = TherapyPostCommentReaction.create(comment, user, CommentReactionType.LIKE);

        ToggleCommentReactionRequest request =
                new ToggleCommentReactionRequest(CommentReactionType.DISLIKE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commentRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(comment));
        when(commentReactionRepository.findByCommentIdAndUserId(20L, 1L)).thenReturn(Optional.of(existing), Optional.of(existing));

        when(commentReactionRepository.countByCommentIdAndReactionType(20L, CommentReactionType.LIKE)).thenReturn(0L);
        when(commentReactionRepository.countByCommentIdAndReactionType(20L, CommentReactionType.DISLIKE)).thenReturn(1L);

        // when
        CommentReactionStatusResponse response = commentReactionService.toggleReaction(1L, 20L, request);

        // then
        assertThat(existing.getReactionType()).isEqualTo(CommentReactionType.DISLIKE);
        assertThat(response.getDislikeCount()).isEqualTo(1L);
        assertThat(response.getMyReactionType()).isEqualTo(CommentReactionType.DISLIKE);
    }
}