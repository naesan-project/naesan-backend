package com.naesan.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

class AbsoluteSessionTimeoutFilterTest {
    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");
    private static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(12);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("절대 만료 전 session 요청은 계속 진행한다")
    void continuesBeforeAbsoluteTimeout() throws Exception {
        AbsoluteSessionTimeoutFilter filter = filter();
        MockHttpServletRequest request = authenticatedRequest(
                NOW.minus(ABSOLUTE_TIMEOUT).plusSeconds(1)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(((MockHttpSession) request.getSession()).isInvalid()).isFalse();
    }

    @Test
    @DisplayName("절대 만료 경계부터 session과 CSRF cookie를 폐기하고 401을 반환한다")
    void rejectsAtAbsoluteTimeout() throws Exception {
        AbsoluteSessionTimeoutFilter filter = filter();
        MockHttpServletRequest request = authenticatedRequest(NOW.minus(ABSOLUTE_TIMEOUT));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpSession session = (MockHttpSession) request.getSession();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
        Cookie csrfCookie = response.getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.getMaxAge()).isZero();
    }

    @Test
    @DisplayName("0 이하의 절대 만료 설정을 거절한다")
    void rejectsNonPositiveTimeout() {
        assertThatThrownBy(() -> new AbsoluteSessionTimeoutFilter(
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ZERO,
                CookieCsrfTokenRepository.withHttpOnlyFalse()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("세션 절대 만료 시간은 0보다 커야 합니다.");
    }

    private AbsoluteSessionTimeoutFilter filter() {
        return new AbsoluteSessionTimeoutFilter(
                Clock.fixed(NOW, ZoneOffset.UTC),
                ABSOLUTE_TIMEOUT,
                CookieCsrfTokenRepository.withHttpOnlyFalse()
        );
    }

    private MockHttpServletRequest authenticatedRequest(Instant authenticatedAt) {
        AuthenticatedAccount principal = new AuthenticatedAccount(
                UUID.randomUUID(),
                "user@example.com",
                authenticatedAt
        );
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of()
        );
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession();
        return request;
    }
}
