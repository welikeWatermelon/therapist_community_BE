package com.therapyCommunity_Vol1.backend.auth.controller;

import com.therapyCommunity_Vol1.backend.auth.dto.LoginRequest;
import com.therapyCommunity_Vol1.backend.auth.dto.LoginResponse;
import com.therapyCommunity_Vol1.backend.auth.dto.RefreshResponse;
import com.therapyCommunity_Vol1.backend.auth.dto.SignupRequest;
import com.therapyCommunity_Vol1.backend.auth.dto.SignupResponse;
import com.therapyCommunity_Vol1.backend.auth.service.AuthService;
import com.therapyCommunity_Vol1.backend.auth.support.RefreshTokenCookieManager;
import com.therapyCommunity_Vol1.backend.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "회원가입, 로그인, 토큰 갱신, 로그아웃")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieManager refreshTokenCookieManager;

    @Operation(summary = "회원가입", description = "이메일, 비밀번호(8자 이상), 약관 동의로 가입. 닉네임 자동 생성, 자동 로그인", security = {})
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        String userAgent = httpServletRequest.getHeader("User-Agent");
        String ipAddress = extractClientIp(httpServletRequest);
        AuthService.SignupResult result = authService.signup(request, userAgent, ipAddress);

        refreshTokenCookieManager.addRefreshTokenCookie(
                httpServletResponse,
                result.refreshToken(),
                result.refreshTokenExpiresInSec()
        );

        return ResponseEntity.ok(ApiResponse.success(result.response()));
    }

    @Operation(summary = "로그인", description = "이메일/비밀번호 인증 후 access token + refresh token 쿠키 발급", security = {})
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        String userAgent = httpServletRequest.getHeader("User-Agent");
        String ipAddress = extractClientIp(httpServletRequest);
        AuthService.LoginResult result = authService.login(request, userAgent, ipAddress);

        refreshTokenCookieManager.addRefreshTokenCookie(
                httpServletResponse,
                result.refreshToken(),
                result.refreshTokenExpiresInSec()
        );

        return ResponseEntity.ok(ApiResponse.success(result.response()));
    }

    @Operation(summary = "토큰 갱신", description = "refresh token 쿠키로 새 access token 발급 (토큰 로테이션)", security = {})
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        String userAgent = httpServletRequest.getHeader("User-Agent");
        String ipAddress = extractClientIp(httpServletRequest);
        String refreshToken = refreshTokenCookieManager.extractRefreshToken(httpServletRequest);
        AuthService.RefreshResult result = authService.refresh(refreshToken, userAgent, ipAddress);

        refreshTokenCookieManager.addRefreshTokenCookie(
                httpServletResponse,
                result.refreshToken(),
                result.refreshTokenExpiresInSec()
        );

        return ResponseEntity.ok(ApiResponse.success(result.response()));
    }

    @Operation(summary = "로그아웃", description = "refresh token 폐기 + 쿠키 만료", security = {})
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse
    ) {
        String refreshToken = refreshTokenCookieManager.extractRefreshToken(httpServletRequest);
        authService.logout(refreshToken);
        refreshTokenCookieManager.expireRefreshTokenCookie(httpServletResponse);
        return ResponseEntity.noContent().build();
    }

    private static String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
