package com.therapyCommunity_Vol1.backend.follow.service;

import com.therapyCommunity_Vol1.backend.follow.domain.Follow;
import com.therapyCommunity_Vol1.backend.follow.dto.FollowCountResponse;
import com.therapyCommunity_Vol1.backend.follow.dto.FollowStatusResponse;
import com.therapyCommunity_Vol1.backend.follow.dto.FollowUserResponse;
import com.therapyCommunity_Vol1.backend.follow.repository.FollowRepository;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import com.therapyCommunity_Vol1.backend.notification.event.NotificationEvent;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private final FollowRepository followRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public FollowStatusResponse follow(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.FOLLOW_SELF_NOT_ALLOWED);
        }

        User target = userService.findUserOrThrow(targetUserId);

        if (target.getRole() != UserRole.THERAPIST) {
            throw new CustomException(ErrorCode.FOLLOW_TARGET_NOT_THERAPIST);
        }

        boolean alreadyExists = followRepository.existsByFollowerIdAndFollowingId(currentUserId, targetUserId);

        if (!alreadyExists) {
            User follower = userService.getReferenceById(currentUserId);

            try {
                Follow follow = Follow.create(follower, target);
                followRepository.save(follow);
            } catch (DataIntegrityViolationException e) {
                return new FollowStatusResponse(targetUserId, true);
            }

            eventPublisher.publishEvent(NotificationEvent.of(
                    currentUserId, targetUserId,
                    NotificationType.NEW_FOLLOW, targetUserId));
        }

        return new FollowStatusResponse(targetUserId, true);
    }

    @Transactional
    public FollowStatusResponse unfollow(Long currentUserId, Long targetUserId) {
        followRepository.findByFollowerIdAndFollowingId(currentUserId, targetUserId)
                .ifPresent(followRepository::delete);

        return new FollowStatusResponse(targetUserId, false);
    }

    public FollowStatusResponse getFollowStatus(Long currentUserId, Long targetUserId) {
        boolean isFollowing = followRepository.existsByFollowerIdAndFollowingId(currentUserId, targetUserId);
        return new FollowStatusResponse(targetUserId, isFollowing);
    }

    public boolean isFollowing(Long followerId, Long followingId) {
        return followRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
    }

    public FollowCountResponse getFollowCounts(Long userId) {
        long followerCount = followRepository.countByFollowingId(userId);
        long followingCount = followRepository.countByFollowerId(userId);
        return new FollowCountResponse(followerCount, followingCount);
    }

    private static final int MAX_PAGE_SIZE = 50;

    public PagedResponse<FollowUserResponse> getFollowers(Long userId, int page, int size) {
        size = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")));
        Page<Follow> result = followRepository.findFollowersByUserId(userId, pageable);

        List<FollowUserResponse> items = result.getContent().stream()
                .map(f -> FollowUserResponse.from(f.getFollower()))
                .toList();

        return PagedResponse.from(result, items);
    }

    public PagedResponse<FollowUserResponse> getFollowings(Long userId, int page, int size) {
        size = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")));
        Page<Follow> result = followRepository.findFollowingsByUserId(userId, pageable);

        List<FollowUserResponse> items = result.getContent().stream()
                .map(f -> FollowUserResponse.from(f.getFollowing()))
                .toList();

        return PagedResponse.from(result, items);
    }

    public List<Long> getFollowingIds(Long followerId) {
        return followRepository.findFollowingIdsByFollowerId(followerId);
    }
}
