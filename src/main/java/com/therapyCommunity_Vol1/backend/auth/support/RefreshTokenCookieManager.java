package com.therapyCommunity_Vol1.backend.auth.support;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class RefreshTokenCookieManager {

    private final String cookieName;
    private final String path;
    private final String sameSite;
    private final boolean secure;

    public RefreshTokenCookieManager(
            @Value("${auth.cookie.refresh.name:refreshToken}") String cookieName,
            @Value("${auth.cookie.refresh.path:/api/v1/auth}") String path,
            @Value("${auth.cookie.refresh.same-site:Lax}") String sameSite,
            @Value("${auth.cookie.refresh.secure:true}") boolean secure
    ) {
        this.cookieName = cookieName;
        this.path = path;
        this.sameSite = sameSite;
        this.secure = secure;
    }

    public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(refreshToken, maxAgeSeconds).toString());
    }

    public void expireRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0).toString());
    }

    public String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_INVALID));
    }

    private ResponseCookie buildCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .sameSite(sameSite)
                .maxAge(maxAgeSeconds)
                .build();
    }
}
