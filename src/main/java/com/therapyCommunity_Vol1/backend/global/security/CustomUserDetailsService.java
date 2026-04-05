package com.therapyCommunity_Vol1.backend.global.security;

import com.therapyCommunity_Vol1.backend.global.cache.UserCacheService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * JWT 인증 필터에서 매 요청마다 호출되는 UserDetailsService.
 *
 * Redis 캐시(UserCacheService)를 통해 DB 조회를 최소화한다.
 * 조회 순서: Null 캐시 확인 → 캐시 조회 → DB 조회 → 캐시 저장
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserCacheService userCacheService;

    @Override
    public UserDetails loadUserByUsername(String userId) {
        Long id = Long.parseLong(userId);

        // 1. Null 캐시 확인 — 존재하지 않는 userId로 반복 조회 차단 (Penetration 방지)
        if (userCacheService.isNullCached(id)) {
            throw new UsernameNotFoundException("User not found");
        }

        // 2. Redis 캐시 조회 — hit 시 DB 조회 없이 반환
        Optional<User> cached = userCacheService.get(id);
        if (cached.isPresent()) {
            return new CustomUserDetails(cached.get());
        }

        // 3. 캐시 miss → DB 조회
        User user = userRepository.findById(id)
                .orElseGet(() -> {
                    userCacheService.putNull(id);  // 존재하지 않는 userId → Null 캐싱
                    return null;
                });

        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }

        // 4. DB 조회 성공 → 캐시 저장 (TTL + jitter)
        userCacheService.put(id, user);
        return new CustomUserDetails(user);
    }
}
