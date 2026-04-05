package com.therapyCommunity_Vol1.backend.global.cache;

import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CachedUser implements Serializable {

    private Long id;
    private String email;
    private String passwordHash;
    private String nickname;
    private String profileImageUrl;
    private String role;
    private LocalDateTime deletedAt;

    public static CachedUser from(User user) {
        return new CachedUser(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getRole().getCode(),
                user.getDeletedAt()
        );
    }

    public User toEntity() {
        return User.builder()
                .id(id)
                .email(email)
                .passwordHash(passwordHash)
                .nickname(nickname)
                .profileImageUrl(profileImageUrl)
                .role(UserRole.valueOf(role))
                .deletedAt(deletedAt)
                .build();
    }
}
