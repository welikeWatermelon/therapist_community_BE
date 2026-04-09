package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.springframework.stereotype.Component;

@Component
public class PostVisibilityAccessPolicy {

    public void checkAccess(TherapyPost post, UserRole role) {
        if (post.getVisibility() == Visibility.PRIVATE && role == UserRole.USER) {
            throw new CustomException(ErrorCode.THERAPIST_VERIFICATION_REQUIRED);
        }
    }

    public void checkCanWritePrivate(UserRole role) {
        if (role == UserRole.USER) {
            throw new CustomException(ErrorCode.THERAPIST_VERIFICATION_REQUIRED);
        }
    }

    public boolean canViewPrivate(UserRole role) {
        return role == UserRole.THERAPIST || role == UserRole.ADMIN;
    }
}
