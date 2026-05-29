package com.therapyCommunity_Vol1.backend.follow.service;

import com.therapyCommunity_Vol1.backend.follow.dto.FollowStatusResponse;
import com.therapyCommunity_Vol1.backend.scrap.service.ScrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowFacade {

    private final FollowService followService;
    private final ScrapService scrapService;

    @Transactional
    public FollowStatusResponse unfollow(Long currentUserId, Long targetUserId) {
        FollowStatusResponse response = followService.unfollow(currentUserId, targetUserId);
        scrapService.deleteScrapsByUnfollow(currentUserId, targetUserId);
        return response;
    }
}
