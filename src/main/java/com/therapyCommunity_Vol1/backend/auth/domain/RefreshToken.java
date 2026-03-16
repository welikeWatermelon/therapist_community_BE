package com.therapyCommunity_Vol1.backend.auth.domain;

import com.therapyCommunity_Vol1.backend.global.domain.BaseEntity;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "token_family", nullable = false, columnDefinition = "uuid")
    private UUID tokenFamily;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_reason", length = 50)
    private String revokedReason;

    private RefreshToken(
            User user,
            String tokenHash,
            UUID tokenFamily,
            String userAgent,
            String ipAddress,
            LocalDateTime expiresAt
    ) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.tokenFamily = tokenFamily;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(
            User user,
            String tokenHash,
            UUID tokenFamily,
            String userAgent,
            String ipAddress,
            LocalDateTime expiresAt
    ) {
        return new RefreshToken(
                user,
                tokenHash,
                tokenFamily,
                userAgent,
                ipAddress,
                expiresAt
        );
    }

    public void revoke(String reason) {
        this.revokedAt = LocalDateTime.now();
        this.revokedReason = reason;
    }

    public boolean isRevoked() {
        return this.revokedAt != null;
    }

    public boolean isExpired() {
        return this.expiresAt.isBefore(LocalDateTime.now());
    }
}