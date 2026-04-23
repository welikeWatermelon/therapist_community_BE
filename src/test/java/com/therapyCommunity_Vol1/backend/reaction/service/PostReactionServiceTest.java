package com.therapyCommunity_Vol1.backend.reaction.service;

import com.therapyCommunity_Vol1.backend.analytics.domain.EventTargetType;
import com.therapyCommunity_Vol1.backend.analytics.domain.UserEventType;
import com.therapyCommunity_Vol1.backend.analytics.event.UserEventPublisher;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.service.ActivePostFinder;
import com.therapyCommunity_Vol1.backend.post.service.PostVisibilityAccessPolicy;
import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostReaction;
import com.therapyCommunity_Vol1.backend.reaction.dto.PostReactionStatusResponse;
import com.therapyCommunity_Vol1.backend.reaction.dto.TogglePostReactionRequest;
import com.therapyCommunity_Vol1.backend.post.service.PostService;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostReactionRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import java.util.List;
import java.util.Optional;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PostReactionServiceTest {

    private TherapyPostReactionRepository postReactionRepository;
    private PostService postService;
    private ActivePostFinder activePostFinder;
    private UserRepository userRepository;
    private ApplicationEventPublisher eventPublisher;
    private PostVisibilityAccessPolicy visibilityPolicy;
    private UserEventPublisher userEventPublisher;
    private PostReactionService postReactionService;

    private User user;
    private TherapyPost post;

    @BeforeEach
    void setUp() {
        postReactionRepository = mock(TherapyPostReactionRepository.class);
        postService = mock(PostService.class);
        activePostFinder = mock(ActivePostFinder.class);
        userRepository = mock(UserRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        visibilityPolicy = mock(PostVisibilityAccessPolicy.class);
        userEventPublisher = mock(UserEventPublisher.class);
        postReactionService = new PostReactionService(
                postReactionRepository, postService, activePostFinder, userRepository, eventPublisher, visibilityPolicy, userEventPublisher
        );

        user = User.builder()
                .id(1L).email("test@test.com").nickname("tester").role(UserRole.THERAPIST)
                .build();
        post = TherapyPost.create("<p>본문</p>", TherapyArea.SPEECH, Visibility.PUBLIC, user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(activePostFinder.findOrThrow(10L)).thenReturn(post);
    }

    /** 기존 반응 없는 상태에서 새 반응 생성 */
    @Test
    void 반응_없음에서_새_반응_생성() {
        when(postReactionRepository.findByPostIdAndUserId(10L, 1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(TherapyPostReaction.create(post, user, PostReactionType.LIKE)));
        when(postReactionRepository.countGroupedByPostId(10L))
                .thenReturn(List.<Object[]>of(new Object[]{PostReactionType.LIKE, 1L}));

        PostReactionStatusResponse response = postReactionService.toggleReaction(
                1L, UserRole.THERAPIST, 10L, new TogglePostReactionRequest(PostReactionType.LIKE)
        );

        verify(postReactionRepository).save(any(TherapyPostReaction.class));
        assertThat(response.getMyReactionType()).isEqualTo(PostReactionType.LIKE);
        assertThat(response.getLikeCount()).isEqualTo(1L);
    }

    /** 같은 반응 다시 클릭 → 삭제 (토글 off) */
    @Test
    void 같은_반응_다시_클릭하면_삭제() {
        TherapyPostReaction existing = TherapyPostReaction.create(post, user, PostReactionType.LIKE);

        when(postReactionRepository.findByPostIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.empty());
        when(postReactionRepository.countGroupedByPostId(10L)).thenReturn(List.of());

        PostReactionStatusResponse response = postReactionService.toggleReaction(
                1L, UserRole.THERAPIST, 10L, new TogglePostReactionRequest(PostReactionType.LIKE)
        );

        verify(postReactionRepository).delete(existing);
        assertThat(response.getMyReactionType()).isNull();
        assertThat(response.getLikeCount()).isEqualTo(0L);
    }

    /** 다른 반응 클릭 → 타입 변경 */
    @Test
    void 다른_반응_클릭하면_타입_변경() {
        TherapyPostReaction existing = TherapyPostReaction.create(post, user, PostReactionType.LIKE);

        when(postReactionRepository.findByPostIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(existing));
        when(postReactionRepository.countGroupedByPostId(10L))
                .thenReturn(List.<Object[]>of(new Object[]{PostReactionType.USEFUL, 1L}));

        PostReactionStatusResponse response = postReactionService.toggleReaction(
                1L, UserRole.THERAPIST, 10L, new TogglePostReactionRequest(PostReactionType.USEFUL)
        );

        assertThat(existing.getReactionType()).isEqualTo(PostReactionType.USEFUL);
        assertThat(response.getUsefulCount()).isEqualTo(1L);
        assertThat(response.getLikeCount()).isEqualTo(0L);
    }

    /** grouped count가 legacy 필드에 정확히 반영됨 */
    @Test
    void grouped_count가_legacy_필드에_반영된다() {
        when(postReactionRepository.findByPostIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        when(postReactionRepository.countGroupedByPostId(10L)).thenReturn(List.of(
                new Object[]{PostReactionType.LIKE, 5L},
                new Object[]{PostReactionType.CURIOUS, 3L},
                new Object[]{PostReactionType.USEFUL, 2L}
        ));

        PostReactionStatusResponse response = postReactionService.getReactionStatus(1L, UserRole.THERAPIST, 10L);

        assertThat(response.getLikeCount()).isEqualTo(5L);
        assertThat(response.getCuriousCount()).isEqualTo(3L);
        assertThat(response.getUsefulCount()).isEqualTo(2L);
        assertThat(response.getReactionCounts()).containsEntry(PostReactionType.LIKE, 5L);
    }

    /** top reaction — count가 가장 큰 타입 */
    @Test
    void top_reaction은_count가_가장_큰_타입() {
        when(postReactionRepository.findByPostIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        when(postReactionRepository.countGroupedByPostId(10L)).thenReturn(List.of(
                new Object[]{PostReactionType.LIKE, 2L},
                new Object[]{PostReactionType.CURIOUS, 5L},
                new Object[]{PostReactionType.USEFUL, 1L}
        ));

        PostReactionStatusResponse response = postReactionService.getReactionStatus(1L, UserRole.THERAPIST, 10L);

        assertThat(response.getTopReactionType()).isEqualTo(PostReactionType.CURIOUS);
        assertThat(response.getTopReactionCount()).isEqualTo(5L);
        assertThat(response.getTopReactionColorToken()).isEqualTo("success");
    }

    /** 동률일 때 displayOrder가 낮은(우선순위 높은) 타입 */
    @Test
    void 동률이면_displayOrder가_낮은_타입이_top() {
        when(postReactionRepository.findByPostIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        // LIKE(order=0)와 USEFUL(order=2)가 동률 3
        when(postReactionRepository.countGroupedByPostId(10L)).thenReturn(List.of(
                new Object[]{PostReactionType.LIKE, 3L},
                new Object[]{PostReactionType.USEFUL, 3L}
        ));

        PostReactionStatusResponse response = postReactionService.getReactionStatus(1L, UserRole.THERAPIST, 10L);

        assertThat(response.getTopReactionType()).isEqualTo(PostReactionType.LIKE);
        assertThat(response.getTopReactionCount()).isEqualTo(3L);
        assertThat(response.getTopReactionColorToken()).isEqualTo("primary");
    }

    /** 반응이 없으면 top reaction 관련 필드 모두 null */
    @Test
    void 반응_없으면_top_reaction은_null() {
        when(postReactionRepository.findByPostIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        when(postReactionRepository.countGroupedByPostId(10L)).thenReturn(List.of());

        PostReactionStatusResponse response = postReactionService.getReactionStatus(1L, UserRole.THERAPIST, 10L);

        assertThat(response.getTopReactionType()).isNull();
        assertThat(response.getTopReactionCount()).isNull();
        assertThat(response.getTopReactionColorToken()).isNull();
        assertThat(response.getLikeCount()).isEqualTo(0L);
        assertThat(response.getCuriousCount()).isEqualTo(0L);
        assertThat(response.getUsefulCount()).isEqualTo(0L);
    }

    /** USER는 PRIVATE 게시글의 반응을 토글할 수 없다 */
    @Test
    void USER는_PRIVATE_게시글에_반응_토글_불가() {
        TherapyPost privatePost = TherapyPost.create("<p>본문</p>", TherapyArea.SPEECH, Visibility.PRIVATE, user);
        when(activePostFinder.findOrThrow(10L)).thenReturn(privatePost);
        doThrow(new CustomException(ErrorCode.THERAPIST_VERIFICATION_REQUIRED))
                .when(visibilityPolicy).checkAccess(privatePost, UserRole.USER);

        assertThatThrownBy(() -> postReactionService.toggleReaction(
                1L, UserRole.USER, 10L, new TogglePostReactionRequest(PostReactionType.LIKE)
        ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.THERAPIST_VERIFICATION_REQUIRED);

        verify(postReactionRepository, never()).save(any(TherapyPostReaction.class));
        verify(postReactionRepository, never()).delete(any(TherapyPostReaction.class));
    }

    /** 새 반응 생성 시 POST_REACT analytics 이벤트가 발행된다 */
    @Test
    void 새_반응_생성시_POST_REACT_이벤트_발행() {
        when(postReactionRepository.findByPostIdAndUserId(10L, 1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(TherapyPostReaction.create(post, user, PostReactionType.LIKE)));
        when(postReactionRepository.countGroupedByPostId(10L)).thenReturn(List.of());

        postReactionService.toggleReaction(
                1L, UserRole.THERAPIST, 10L, new TogglePostReactionRequest(PostReactionType.LIKE)
        );

        verify(userEventPublisher).publish(
                eq(1L),
                eq(UserEventType.POST_REACT),
                eq(EventTargetType.POST),
                eq(10L),
                eq(Map.of("reactionType", "LIKE"))
        );
    }

    /** 타입 변경 시에도 POST_REACT 이벤트 발행 (여전히 positive signal) */
    @Test
    void 타입_변경시_POST_REACT_이벤트_발행() {
        TherapyPostReaction existing = TherapyPostReaction.create(post, user, PostReactionType.LIKE);
        when(postReactionRepository.findByPostIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(existing));
        when(postReactionRepository.countGroupedByPostId(10L)).thenReturn(List.of());

        postReactionService.toggleReaction(
                1L, UserRole.THERAPIST, 10L, new TogglePostReactionRequest(PostReactionType.USEFUL)
        );

        verify(userEventPublisher).publish(
                eq(1L),
                eq(UserEventType.POST_REACT),
                eq(EventTargetType.POST),
                eq(10L),
                eq(Map.of("reactionType", "USEFUL"))
        );
    }

    /** 같은 반응 재클릭(삭제)에는 analytics 이벤트 미발행 (부정 시그널 미수집) */
    @Test
    void 같은_반응_삭제시_analytics_이벤트_미발행() {
        TherapyPostReaction existing = TherapyPostReaction.create(post, user, PostReactionType.LIKE);
        when(postReactionRepository.findByPostIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.empty());
        when(postReactionRepository.countGroupedByPostId(10L)).thenReturn(List.of());

        postReactionService.toggleReaction(
                1L, UserRole.THERAPIST, 10L, new TogglePostReactionRequest(PostReactionType.LIKE)
        );

        verify(userEventPublisher, never()).publish(anyLong(), any(), any(), anyLong(), any());
        verify(userEventPublisher, never()).publish(anyLong(), any(), any(), anyLong());
    }

    /** USER는 PRIVATE 게시글의 반응 상태를 조회할 수 없다 */
    @Test
    void USER는_PRIVATE_게시글의_반응_상태_조회_불가() {
        TherapyPost privatePost = TherapyPost.create("<p>본문</p>", TherapyArea.SPEECH, Visibility.PRIVATE, user);
        when(activePostFinder.findOrThrow(10L)).thenReturn(privatePost);
        doThrow(new CustomException(ErrorCode.THERAPIST_VERIFICATION_REQUIRED))
                .when(visibilityPolicy).checkAccess(privatePost, UserRole.USER);

        assertThatThrownBy(() -> postReactionService.getReactionStatus(1L, UserRole.USER, 10L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.THERAPIST_VERIFICATION_REQUIRED);

        verify(postReactionRepository, never()).countGroupedByPostId(anyLong());
    }
}
