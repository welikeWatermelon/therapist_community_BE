package com.therapyCommunity_Vol1.backend.follow.service;

import com.therapyCommunity_Vol1.backend.follow.domain.Follow;
import com.therapyCommunity_Vol1.backend.follow.dto.FollowCountResponse;
import com.therapyCommunity_Vol1.backend.follow.dto.FollowStatusResponse;
import com.therapyCommunity_Vol1.backend.follow.dto.FollowUserResponse;
import com.therapyCommunity_Vol1.backend.follow.repository.FollowRepository;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FollowServiceTest {

    private FollowRepository followRepository;
    private UserService userService;
    private ApplicationEventPublisher eventPublisher;
    private FollowService followService;

    private User userFollower;
    private User therapistTarget;

    @BeforeEach
    void setUp() {
        followRepository = mock(FollowRepository.class);
        userService = mock(UserService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        followService = new FollowService(followRepository, userService, eventPublisher);

        userFollower = User.builder()
                .id(1L).email("user@test.com").nickname("유저").role(UserRole.USER).build();
        therapistTarget = User.builder()
                .id(2L).email("therapist@test.com").nickname("치료사").role(UserRole.THERAPIST).build();
    }

    @Test
    void 팔로우_성공() {
        when(userService.findUserOrThrow(2L)).thenReturn(therapistTarget);
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(false);
        when(userService.findUserOrThrow(1L)).thenReturn(userFollower);
        when(followRepository.save(any(Follow.class))).thenAnswer(inv -> inv.getArgument(0));

        FollowStatusResponse response = followService.follow(1L, 2L);

        assertThat(response.getUserId()).isEqualTo(2L);
        assertThat(response.isFollowing()).isTrue();
        verify(followRepository).save(any(Follow.class));
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void 이미_팔로우중이면_멱등응답() {
        when(userService.findUserOrThrow(2L)).thenReturn(therapistTarget);
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);

        FollowStatusResponse response = followService.follow(1L, 2L);

        assertThat(response.isFollowing()).isTrue();
        verify(followRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 자기자신_팔로우_불가() {
        assertThatThrownBy(() -> followService.follow(1L, 1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_SELF_NOT_ALLOWED);
    }

    @Test
    void 치료사가_아닌_대상_팔로우_불가() {
        User normalUser = User.builder()
                .id(3L).email("normal@test.com").nickname("일반유저").role(UserRole.USER).build();
        when(userService.findUserOrThrow(3L)).thenReturn(normalUser);

        assertThatThrownBy(() -> followService.follow(1L, 3L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.FOLLOW_TARGET_NOT_THERAPIST);
    }

    @Test
    void 언팔로우_성공() {
        Follow follow = Follow.create(userFollower, therapistTarget);
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.of(follow));

        FollowStatusResponse response = followService.unfollow(1L, 2L);

        assertThat(response.isFollowing()).isFalse();
        verify(followRepository).delete(follow);
    }

    @Test
    void 팔로우관계_없어도_언팔로우_에러없음() {
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty());

        FollowStatusResponse response = followService.unfollow(1L, 2L);

        assertThat(response.isFollowing()).isFalse();
        verify(followRepository, never()).delete(any());
    }

    @Test
    void 팔로우_상태_조회() {
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);

        FollowStatusResponse response = followService.getFollowStatus(1L, 2L);

        assertThat(response.isFollowing()).isTrue();
    }

    @Test
    void 팔로우_카운트_조회() {
        when(followRepository.countByFollowingId(2L)).thenReturn(10L);
        when(followRepository.countByFollowerId(2L)).thenReturn(5L);

        FollowCountResponse response = followService.getFollowCounts(2L);

        assertThat(response.getFollowerCount()).isEqualTo(10L);
        assertThat(response.getFollowingCount()).isEqualTo(5L);
    }

    @Test
    void 팔로워_목록_조회() {
        Follow follow = Follow.create(userFollower, therapistTarget);
        when(followRepository.findFollowersByUserId(eq(2L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(follow)));

        PagedResponse<FollowUserResponse> response = followService.getFollowers(2L, 0, 10);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getUserId()).isEqualTo(1L);
    }

    @Test
    void 팔로잉_목록_조회() {
        Follow follow = Follow.create(userFollower, therapistTarget);
        when(followRepository.findFollowingsByUserId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(follow)));

        PagedResponse<FollowUserResponse> response = followService.getFollowings(1L, 0, 10);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getUserId()).isEqualTo(2L);
    }

    @Test
    void 팔로잉_ID_목록_조회() {
        when(followRepository.findFollowingIdsByFollowerId(1L)).thenReturn(List.of(2L, 3L));

        List<Long> ids = followService.getFollowingIds(1L);

        assertThat(ids).containsExactly(2L, 3L);
    }
}
