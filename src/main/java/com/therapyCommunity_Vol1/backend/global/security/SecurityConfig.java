package com.therapyCommunity_Vol1.backend.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .headers(headers -> headers
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
            )
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(
                            "/api/v1/auth/signup",
                            "/api/v1/auth/refresh",
                            "/api/v1/auth/logout",
                            "/api/v1/auth/login",
                            "/api/v1/meta/**",
                            "/api/v1/terms/**",
                            "/api/v1/home",
                            "/api/v1/health",
                            "/actuator/health",
                            "/swagger-ui/**",
                            "/v3/api-docs/**",
                            "/api/v1/me/profile-image/**"
                    ).permitAll()
                    // 알림 API — 로그인한 사용자 모두
                    .requestMatchers("/api/v1/notifications/**").authenticated()
                    // 쪽지 API — 로그인한 사용자 모두
                    .requestMatchers("/api/v1/messages/**").authenticated()
                    .requestMatchers("/api/v1/me/messages/**").authenticated()
                    // 관리자 API
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                    // 커뮤니티 — USER 포함 (서비스에서 PUBLIC/PRIVATE 체크)
                    .requestMatchers("/api/v1/posts/**").hasAnyRole("USER", "THERAPIST", "ADMIN")
                    .requestMatchers("/api/v1/comments/**").hasAnyRole("USER", "THERAPIST", "ADMIN")
                    .requestMatchers("/api/v1/me/scraps", "/api/v1/me/scraps/**").hasAnyRole("USER", "THERAPIST", "ADMIN")
                    .requestMatchers("/api/v1/me/downloads", "/api/v1/me/downloads/**").hasAnyRole("USER", "THERAPIST", "ADMIN")

                    //나머지는 로그인 필요
                    .anyRequest().authenticated()
            );

        http.addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider, customUserDetailsService),
                UsernamePasswordAuthenticationFilter.class
        );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization","Content-Type"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
