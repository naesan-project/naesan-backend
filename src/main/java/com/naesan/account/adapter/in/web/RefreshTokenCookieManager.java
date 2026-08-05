package com.naesan.account.adapter.in.web;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import com.naesan.security.TokenSession;

public final class RefreshTokenCookieManager {
    static final String COOKIE_NAME = "NAESAN_REFRESH_TOKEN";
    private static final String COOKIE_PATH = "/api/sessions";

    private final boolean secure;
    private final Duration timeToLive;

    public RefreshTokenCookieManager(boolean secure, Duration timeToLive) {
        this.secure = secure;
        this.timeToLive = Objects.requireNonNull(timeToLive);
        if (timeToLive.isNegative() || timeToLive.isZero()) {
            throw new IllegalArgumentException("Refresh token cookie 유효 기간은 0보다 커야 합니다.");
        }
    }

    public Optional<String> read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    public void write(HttpServletResponse response, TokenSession session) {
        addCookie(response, session.rawRefreshToken(), timeToLive);
    }

    public void clear(HttpServletResponse response) {
        addCookie(response, "", Duration.ZERO);
    }

    private void addCookie(
            HttpServletResponse response,
            String value,
            Duration maxAge
    ) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
