package com.therapyCommunity_Vol1.backend.global.cache;

import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Redis에 저장되는 User 캐시 DTO.
 * JPA 엔티티(User)를 직접 직렬화하면 프록시/지연로딩 문제가 발생하므로
 * 순수 POJO로 변환하여 JSON 직렬화한다.
 */
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

    /** User 엔티티 → 캐시 DTO 변환 */
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

    /** 캐시 DTO → User 엔티티 복원 (비영속 상태, JPA 관리 X) */
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
