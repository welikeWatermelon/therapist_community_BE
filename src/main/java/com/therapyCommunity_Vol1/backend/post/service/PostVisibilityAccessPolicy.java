package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.follow.service.FollowService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostVisibilityAccessPolicy {

    private final FollowService followService;

    public void checkAccess(TherapyPost post, UserRole role) {
        checkAccess(post, role, null);
    }

    /**
     * 게시글 접근 권한 검증.
     *
     * 설계 결정:
     * - ADMIN도 FOLLOWERS_ONLY 게시글 접근 시 팔로우 필요 (인스타그램 비공계 모델 일관성).
     *   ADMIN 강제 접근이 필요하면 별도 관리자 API로 분리할 것.
     * - 역할 강등(THERAPIST→USER) 시 기존 팔로우 관계는 즉시 정리하지 않음.
     *   강등 자체가 극히 드문 관리자 작업이고, 피드 쿼리의 deletedAt/visibility 필터가
     *   실질적 노출을 방어함. 강등 빈도가 높아지면 이벤트 기반 일괄 삭제 도입 검토.
     */
    public void checkAccess(TherapyPost post, UserRole role, Long currentUserId) {
        Visibility visibility = post.getVisibility();

        // 작성자 본인은 항상 접근 가능
        if (currentUserId != null && currentUserId.equals(post.getAuthor().getId())) {
            return;
        }

        switch (visibility) {
            case PUBLIC -> { /* 모든 사용자 접근 가능 */ }
            case PRIVATE -> {
                if (role == UserRole.USER) {
                    throw new CustomException(ErrorCode.THERAPIST_VERIFICATION_REQUIRED);
                }
            }
            case FOLLOWERS_ONLY -> {
                if (currentUserId == null || !followService.isFollowing(currentUserId, post.getAuthor().getId())) {
                    throw new CustomException(ErrorCode.POST_ACCESS_DENIED);
                }
            }
            case VERIFIED_FOLLOWERS_ONLY -> {
                if (role == UserRole.USER) {
                    throw new CustomException(ErrorCode.THERAPIST_VERIFICATION_REQUIRED);
                }
                if (currentUserId == null || !followService.isFollowing(currentUserId, post.getAuthor().getId())) {
                    throw new CustomException(ErrorCode.POST_ACCESS_DENIED);
                }
            }
        }
    }

    public void checkCanWriteVisibility(Visibility visibility, UserRole role) {
        if (visibility == Visibility.PRIVATE
                || visibility == Visibility.FOLLOWERS_ONLY
                || visibility == Visibility.VERIFIED_FOLLOWERS_ONLY) {
            if (role == UserRole.USER) {
                throw new CustomException(ErrorCode.THERAPIST_VERIFICATION_REQUIRED);
            }
        }
    }

    public boolean canViewPrivate(UserRole role) {
        return role == UserRole.THERAPIST || role == UserRole.ADMIN;
    }
}
