package com.therapyCommunity_Vol1.backend.global.security;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.springframework.stereotype.Component;

@Component
public class ResourceAccessValidator {

    public void validateAuthorOrAdmin(Long authorId, Long currentUserId,
                                       UserRole currentUserRole, ErrorCode errorCode) {
        if (currentUserRole != UserRole.ADMIN && !authorId.equals(currentUserId)) {
            throw new CustomException(errorCode);
        }
    }
}
