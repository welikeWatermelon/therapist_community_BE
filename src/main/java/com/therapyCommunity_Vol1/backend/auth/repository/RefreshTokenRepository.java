package com.therapyCommunity_Vol1.backend.auth.repository;

import com.therapyCommunity_Vol1.backend.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByTokenFamilyAndRevokedAtIsNull(UUID tokenFamily);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);
}
