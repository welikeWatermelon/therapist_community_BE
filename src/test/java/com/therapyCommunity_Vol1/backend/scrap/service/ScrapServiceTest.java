package com.therapyCommunity_Vol1.backend.scrap.service;

import com.therapyCommunity_Vol1.backend.analytics.domain.EventTargetType;
import com.therapyCommunity_Vol1.backend.analytics.domain.UserEventType;
import com.therapyCommunity_Vol1.backend.analytics.event.UserEventPublisher;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.service.ActivePostFinder;
import com.therapyCommunity_Vol1.backend.post.service.PostVisibilityAccessPolicy;
import com.therapyCommunity_Vol1.backend.scrap.repository.TherapyPostScrapRepository;
import com.therapyCommunity_Vol1.backend.scrap.domain.TherapyPostScrap;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.scrap.dto.ScrappedPostResponse;
import com.therapyCommunity_Vol1.backend.scrap.dto.ScrapStatusResponse;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ScrapServiceTest {

    private TherapyPostScrapRepository scrapRepository;
    private ActivePostFinder activePostFinder;
    private UserService userService;
    private ApplicationEventPublisher eventPublisher;
    private PostVisibilityAccessPolicy visibilityPolicy;
    private UserEventPublisher userEventPublisher;
    private ScrapService scrapService;

    @BeforeEach
    void setUp() {
        this.scrapRepository = mock(TherapyPostScrapRepository.class);
        this.activePostFinder = mock(ActivePostFinder.class);
        this.userService = mock(UserService.class);
        this.eventPublisher = mock(ApplicationEventPublisher.class);
        this.visibilityPolicy = mock(PostVisibilityAccessPolicy.class);
        when(visibilityPolicy.canViewPrivate(UserRole.THERAPIST)).thenReturn(true);
        when(visibilityPolicy.canViewPrivate(UserRole.ADMIN)).thenReturn(true);
        when(visibilityPolicy.canViewPrivate(UserRole.USER)).thenReturn(false);
        this.userEventPublisher = mock(UserEventPublisher.class);
        this.scrapService = new ScrapService(scrapRepository, activePostFinder, userService, eventPublisher, visibilityPolicy, userEventPublisher);
    }

    @Test
    void 게시글_스크랩_최초저장() {

        // given
        Long currentUserId = 1L;
        Long postId = 10L;

        User user = User.builder()
                .id(currentUserId)
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
        when(userService.findById(currentUserId)).thenReturn(user);
        when(activePostFinder.findOrThrow(postId)).thenReturn(post);
        when(scrapRepository.existsByPostIdAndUserId(postId, currentUserId)).thenReturn(false);

        // when
        ScrapStatusResponse response = scrapService.addScrap(currentUserId, UserRole.THERAPIST, postId);

        // then
        verify(scrapRepository).save(any(TherapyPostScrap.class));
        assertThat(response.isScrapped()).isTrue();
        assertThat(response.getPostId()).isEqualTo(postId);
    }

    @Test
    void 이미_스크랩한_글을_다시저장해도_중복저장하지_않는다() {

        //given
        Long currentUserId = 1L;
        Long postId = 10L;

        User user = User.builder()
                .id(currentUserId)
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

        when(userService.findById(currentUserId)).thenReturn(user);
        when(activePostFinder.findOrThrow(postId)).thenReturn(post);
        when(scrapRepository.existsByPostIdAndUserId(postId, currentUserId)).thenReturn(true);

        // when
        ScrapStatusResponse response = scrapService.addScrap(currentUserId, UserRole.THERAPIST, postId);

        // then
        verify(scrapRepository, never()).save(any(TherapyPostScrap.class));
        assertThat(response.isScrapped()).isTrue();
    }

    @Test
    void 최초_스크랩_시_POST_SCRAP_이벤트_발행() {
        Long currentUserId = 1L;
        Long postId = 10L;
        User user = User.builder().id(currentUserId).email("t@t.com").nickname("tester").role(UserRole.THERAPIST).build();
        TherapyPost post = TherapyPost.create("<p>본문</p>", TherapyArea.SPEECH, Visibility.PUBLIC, user);

        when(userService.findById(currentUserId)).thenReturn(user);
        when(activePostFinder.findOrThrow(postId)).thenReturn(post);
        when(scrapRepository.existsByPostIdAndUserId(postId, currentUserId)).thenReturn(false);

        scrapService.addScrap(currentUserId, UserRole.THERAPIST, postId);

        verify(userEventPublisher).publish(
                eq(currentUserId),
                eq(UserEventType.POST_SCRAP),
                eq(EventTargetType.POST),
                eq(postId)
        );
    }

    @Test
    void 중복_스크랩_요청에는_analytics_이벤트_미발행() {
        Long currentUserId = 1L;
        Long postId = 10L;
        User user = User.builder().id(currentUserId).email("t@t.com").nickname("tester").role(UserRole.THERAPIST).build();
        TherapyPost post = TherapyPost.create("<p>본문</p>", TherapyArea.SPEECH, Visibility.PUBLIC, user);

        when(userService.findById(currentUserId)).thenReturn(user);
        when(activePostFinder.findOrThrow(postId)).thenReturn(post);
        when(scrapRepository.existsByPostIdAndUserId(postId, currentUserId)).thenReturn(true);

        scrapService.addScrap(currentUserId, UserRole.THERAPIST, postId);

        verify(userEventPublisher, never()).publish(anyLong(), any(), any(), anyLong());
        verify(userEventPublisher, never()).publish(anyLong(), any(), any(), anyLong(), any());
    }

    @Test
    void 스크랩_해제_성공() {

        //given
        Long currentUserId = 1L;
        Long postId = 10L;

        User user = User.builder()
                .id(currentUserId)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                user);

        TherapyPostScrap scrap = TherapyPostScrap.create(post,user);

        when(activePostFinder.findOrThrow(postId)).thenReturn(post);
        when(scrapRepository.findByPostIdAndUserId(postId, currentUserId)).thenReturn(Optional.of(scrap));

        // when
        ScrapStatusResponse response = scrapService.removeScrap(currentUserId, UserRole.THERAPIST, postId);

        // then
        verify(scrapRepository).delete(scrap);
        assertThat(response.isScrapped()).isFalse();
    }

    @Test
    void 내_스크랩_목록_조히_성공() {

        //given
        Long currentUserId = 1L;
        Long postId = 10L;

        User user = User.builder()
                .id(currentUserId)
                .email("test@test.com")
                .nickname("tester")
                .role(UserRole.THERAPIST)
                .build();

        TherapyPost post = TherapyPost.create(
                "<p>본문</p>",
                TherapyArea.SPEECH,
                Visibility.PUBLIC,
                user);

        TherapyPostScrap scrap = TherapyPostScrap.create(post,user);

        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")));
        Page<TherapyPostScrap> page = new PageImpl<>(List.of(scrap), pageable, 1);

        when(userService.findById(currentUserId)).thenReturn(user);
        when(scrapRepository.findByUserIdAndPost_DeletedAtIsNull(eq(currentUserId),any(Pageable.class)))
                .thenReturn(page);

        // when
        PagedResponse<ScrappedPostResponse> response = scrapService.getMyScraps(currentUserId, UserRole.THERAPIST, 0, 10);

        // then
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1L);
    }
}