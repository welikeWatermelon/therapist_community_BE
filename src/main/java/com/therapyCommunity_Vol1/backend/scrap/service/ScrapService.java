package com.therapyCommunity_Vol1.backend.scrap.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import com.therapyCommunity_Vol1.backend.notification.event.NotificationEvent;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.service.ActivePostFinder;
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
    private final ActivePostFinder activePostFinder;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Set<Long> getScrappedPostIds(Long userId, List<Long> postIds) {
        if (userId == null || postIds.isEmpty()) {
            return Collections.emptySet();
        }
        return scrapRepository.findScrappedPostIdsByUserIdAndPostIdIn(userId, postIds);
    }

    @Transactional
    public ScrapStatusResponse addScrap(Long currentUserId, Long postId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        TherapyPost post = activePostFinder.findOrThrow(postId);

        boolean alreadyExists = scrapRepository.existsByPostIdAndUserId(postId,currentUserId);

        if (!alreadyExists) {
            TherapyPostScrap scrap = TherapyPostScrap.create(post,user);
            scrapRepository.save(scrap);

            eventPublisher.publishEvent(NotificationEvent.builder()
                    .senderId(currentUserId)
                    .receiverIds(List.of(post.getAuthor().getId()))
                    .type(NotificationType.NEW_SCRAP)
                    .referenceId(postId)
                    .content(user.getNickname() + "님이 회원님의 게시글을 스크랩했습니다.")
                    .build());
        }

        return new ScrapStatusResponse(postId, true);
    }

    @Transactional
    public ScrapStatusResponse removeScrap(Long currentUserId, Long postId) {
        activePostFinder.findOrThrow(postId);

        scrapRepository.findByPostIdAndUserId(postId, currentUserId)
                .ifPresent(scrapRepository::delete);
        return new ScrapStatusResponse(postId, false);
    }

    public ScrapStatusResponse getScrapStatus(Long currentUserId, Long postId) {
        activePostFinder.findOrThrow(postId);
        boolean scrapped = scrapRepository.existsByPostIdAndUserId(postId, currentUserId);

        return new ScrapStatusResponse(postId, scrapped);
    }

    public PagedResponse<ScrappedPostResponse> getMyScraps(Long currentUserId, int page, int size) {
        userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<TherapyPostScrap> result = scrapRepository.findByUserIdAndPost_DeletedAtIsNull(currentUserId, pageable);

        List<ScrappedPostResponse> scraps = result.getContent()
                .stream()
                .map(ScrappedPostResponse::from)
                .toList();

        return PagedResponse.from(result, scraps);
    }
}
