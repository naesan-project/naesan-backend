package com.naesan.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {
    private final Clock clock;
    private final Duration absoluteTimeout;
    private final LogoutHandler logoutHandler;

    public AbsoluteSessionTimeoutFilter(
            Clock clock,
            Duration absoluteTimeout,
            CsrfTokenRepository csrfTokenRepository
    ) {
        this.clock = Objects.requireNonNull(clock);
        this.absoluteTimeout = requirePositive(absoluteTimeout);
        this.logoutHandler = new CompositeLogoutHandler(
                new SecurityContextLogoutHandler(),
                new CsrfLogoutHandler(Objects.requireNonNull(csrfTokenRepository))
        );
    }

    private Duration requirePositive(Duration absoluteTimeout) {
        Objects.requireNonNull(absoluteTimeout);
        if (absoluteTimeout.isZero() || absoluteTimeout.isNegative()) {
            throw new IllegalArgumentException("세션 절대 만료 시간은 0보다 커야 합니다.");
        }
        return absoluteTimeout;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isExpired(authentication)) {
            logoutHandler.logout(request, response, authentication);
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExpired(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedAccount account)) {
            return false;
        }

        Instant expiresAt = account.authenticatedAt().plus(absoluteTimeout);
        return !Instant.now(clock).isBefore(expiresAt);
    }
}
