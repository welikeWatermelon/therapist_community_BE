package com.therapyCommunity_Vol1.backend.scrap.service;

import com.therapyCommunity_Vol1.backend.analytics.domain.EventTargetType;
import com.therapyCommunity_Vol1.backend.analytics.domain.UserEventType;
import com.therapyCommunity_Vol1.backend.analytics.event.UserEventPublisher;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import com.therapyCommunity_Vol1.backend.notification.event.NotificationEvent;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.service.ActivePostFinder;
import com.therapyCommunity_Vol1.backend.post.service.PostService;
import com.therapyCommunity_Vol1.backend.post.service.PostVisibilityAccessPolicy;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.scrap.repository.TherapyPostScrapRepository;
import com.therapyCommunity_Vol1.backend.scrap.domain.TherapyPostScrap;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.scrap.dto.ScrapStatusResponse;
import com.therapyCommunity_Vol1.backend.scrap.dto.ScrappedPostResponse;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScrapService {

    private final TherapyPostScrapRepository scrapRepository;
    private final PostService postService;
    private final ActivePostFinder activePostFinder;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PostVisibilityAccessPolicy visibilityPolicy;
    private final UserEventPublisher userEventPublisher;

    public Set<Long> getScrappedPostIds(Long userId, List<Long> postIds) {
        if (userId == null || postIds.isEmpty()) {
            return Collections.emptySet();
        }
        return scrapRepository.findScrappedPostIdsByUserIdAndPostIdIn(userId, postIds);
    }

    @Transactional
    public ScrapStatusResponse addScrap(Long currentUserId, UserRole currentUserRole, Long postId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole, currentUserId);

        boolean alreadyExists = scrapRepository.existsByPostIdAndUserId(postId,currentUserId);

        if (!alreadyExists) {
            TherapyPostScrap scrap = TherapyPostScrap.create(post,user);
            scrapRepository.save(scrap);
            postService.recalculatePopularityScore(postId);

            eventPublisher.publishEvent(NotificationEvent.of(
                    currentUserId, post.getAuthor().getId(),
                    NotificationType.NEW_SCRAP, postId));

            // 이미 스크랩한 상태에서 재요청은 멱등 응답이므로 수집하지 않음.
            userEventPublisher.publish(
                    currentUserId,
                    UserEventType.POST_SCRAP,
                    EventTargetType.POST,
                    postId
            );
        }

        return new ScrapStatusResponse(postId, true);
    }

    @Transactional
    public ScrapStatusResponse removeScrap(Long currentUserId, UserRole currentUserRole, Long postId) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole, currentUserId);

        scrapRepository.findByPostIdAndUserId(postId, currentUserId)
                .ifPresent(scrap -> {
                    scrapRepository.delete(scrap);
                    postService.recalculatePopularityScore(postId);
                });
        return new ScrapStatusResponse(postId, false);
    }

    public ScrapStatusResponse getScrapStatus(Long currentUserId, UserRole currentUserRole, Long postId) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole, currentUserId);
        boolean scrapped = scrapRepository.existsByPostIdAndUserId(postId, currentUserId);

        return new ScrapStatusResponse(postId, scrapped);
    }

    public PagedResponse<ScrappedPostResponse> getMyScraps(Long currentUserId, UserRole currentUserRole, int page, int size) {
        userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<TherapyPostScrap> result = visibilityPolicy.canViewPrivate(currentUserRole)
                ? scrapRepository.findByUserIdAndPost_DeletedAtIsNull(currentUserId, pageable)
                : scrapRepository.findByUserIdAndPost_DeletedAtIsNullAndPost_Visibility(currentUserId, Visibility.PUBLIC, pageable);

        List<ScrappedPostResponse> scraps = result.getContent()
                .stream()
                .map(ScrappedPostResponse::from)
                .toList();

        return PagedResponse.from(result, scraps);
    }

    @Transactional
    public void deleteScrapsByUnfollow(Long userId, Long unfollowedAuthorId) {
        scrapRepository.deleteByUserIdAndPostAuthorIdAndPostVisibilityIn(
                userId, unfollowedAuthorId,
                List.of(Visibility.FOLLOWERS_ONLY, Visibility.VERIFIED_FOLLOWERS_ONLY));
    }
}
