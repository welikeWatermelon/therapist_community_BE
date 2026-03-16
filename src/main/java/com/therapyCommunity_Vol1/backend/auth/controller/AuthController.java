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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieManager refreshTokenCookieManager;

    @Operation(security = {})
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @RequestBody SignupRequest request
    ) {
        SignupResponse response = authService.signup(request);

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    @Operation(security = {})
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

    @Operation(security = {})
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

    @Operation(security = {})
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
