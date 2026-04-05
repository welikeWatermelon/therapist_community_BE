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

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserCacheService userCacheService;

    @Override
    public UserDetails loadUserByUsername(String userId) {
        Long id = Long.parseLong(userId);

        if (userCacheService.isNullCached(id)) {
            throw new UsernameNotFoundException("User not found");
        }

        Optional<User> cached = userCacheService.get(id);
        if (cached.isPresent()) {
            return new CustomUserDetails(cached.get());
        }

        User user = userRepository.findById(id)
                .orElseGet(() -> {
                    userCacheService.putNull(id);
                    return null;
                });

        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }

        userCacheService.put(id, user);
        return new CustomUserDetails(user);
    }
}
