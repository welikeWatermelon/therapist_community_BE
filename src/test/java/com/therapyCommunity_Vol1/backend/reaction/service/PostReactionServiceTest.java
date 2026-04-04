package com.therapyCommunity_Vol1.backend.reaction.service;

import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostReaction;
import com.therapyCommunity_Vol1.backend.reaction.dto.PostReactionStatusResponse;
import com.therapyCommunity_Vol1.backend.reaction.dto.TogglePostReactionRequest;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostReactionRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PostReactionServiceTest {

    private TherapyPostReactionRepository postReactionRepository;
    private TherapyPostRepository therapyPostRepository;
    private UserRepository userRepository;
    private PostReactionService postReactionService;

    @BeforeEach
    void setUp() {
        postReactionRepository = mock(TherapyPostReactionRepository.class);
        therapyPostRepository = mock(TherapyPostRepository.class);
        userRepository = mock(UserRepository.class);
        postReactionService = new PostReactionService(
                postReactionRepository,
                therapyPostRepository,
                userRepository
        );
    }

    @Test
    void 게시글_반응_최초등록() {

        //given
        User user = User.builder()
                .id(1L)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                user
        );
        TogglePostReactionRequest request = new TogglePostReactionRequest(PostReactionType.EMPATHY);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(therapyPostRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

        when(postReactionRepository.countByPostIdAndReactionType(10L, PostReactionType.EMPATHY)).thenReturn(1L);
        when(postReactionRepository.countByPostIdAndReactionType(10L, PostReactionType.APPRECIATE)).thenReturn(0L);
        when(postReactionRepository.countByPostIdAndReactionType(10L, PostReactionType.HELPFUL)).thenReturn(0L);

        TherapyPostReaction savedReaction = TherapyPostReaction.create(post, user, PostReactionType.EMPATHY);
        when(postReactionRepository.findByPostIdAndUserId(10L, 1L)).thenReturn(Optional.empty(), Optional.of(savedReaction));

        // when
        PostReactionStatusResponse response = postReactionService.toggleReaction(1L, 10L, request);

        // then
        verify(postReactionRepository).save(any(TherapyPostReaction.class));
        assertThat(response.getEmpathyCount()).isEqualTo(1L);
        assertThat(response.getMyReactionType()).isEqualTo(PostReactionType.EMPATHY);
    }

    @Test
    void 같은게시판_같은반응_다시누르면_취소() {
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
                Visibility.PUBLIC,
                user
        );

        TherapyPostReaction existing = TherapyPostReaction.create(post, user, PostReactionType.EMPATHY);

        TogglePostReactionRequest request = new TogglePostReactionRequest(PostReactionType.EMPATHY);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(therapyPostRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(10L, 1L)).thenReturn(Optional.of(existing), Optional.empty());

        when(postReactionRepository.countByPostIdAndReactionType(10L, PostReactionType.EMPATHY)).thenReturn(0L);
        when(postReactionRepository.countByPostIdAndReactionType(10L, PostReactionType.APPRECIATE)).thenReturn(0L);
        when(postReactionRepository.countByPostIdAndReactionType(10L, PostReactionType.HELPFUL)).thenReturn(0L);

        //when
        PostReactionStatusResponse response = postReactionService.toggleReaction(1L, 10L, request);

        // then
        verify(postReactionRepository).delete(existing);
        assertThat(response.getMyReactionType()).isNull();
        assertThat(response.getEmpathyCount()).isEqualTo(0L);
    }

    @Test
    void 다른_게시글반응을_누르면_교체된다() {
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
                Visibility.PUBLIC,
                user
        );

        TherapyPostReaction existing = TherapyPostReaction.create(post, user, PostReactionType.EMPATHY);

        TogglePostReactionRequest request = new TogglePostReactionRequest(PostReactionType.HELPFUL);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(therapyPostRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(10L, 1L)).thenReturn(Optional.of(existing), Optional.of(existing));


        when(postReactionRepository.countByPostIdAndReactionType(10L, PostReactionType.EMPATHY)).thenReturn(0L);
        when(postReactionRepository.countByPostIdAndReactionType(10L, PostReactionType.APPRECIATE)).thenReturn(0L);
        when(postReactionRepository.countByPostIdAndReactionType(10L, PostReactionType.HELPFUL)).thenReturn(1L);

        // when
        PostReactionStatusResponse response = postReactionService.toggleReaction(1L, 10L, request);

        //then
        assertThat(existing.getReactionType()).isEqualTo(PostReactionType.HELPFUL);
        assertThat(response.getHelpfulCount()).isEqualTo(1L);
        assertThat(response.getMyReactionType()).isEqualTo(PostReactionType.HELPFUL);
    }
}
