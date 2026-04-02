package com.therapyCommunity_Vol1.backend.user.domain;

import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public void promoteToTherapist() {
        this.role = UserRole.THERAPIST;
    }

    public void demoteToUser() {
        this.role = UserRole.USER;
    }
}
