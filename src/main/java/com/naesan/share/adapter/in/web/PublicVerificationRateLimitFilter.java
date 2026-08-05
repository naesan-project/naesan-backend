package com.naesan.share.adapter.in.web;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public final class PublicVerificationRateLimitFilter extends OncePerRequestFilter {
    private static final String VERIFICATION_PATH =
            "/api/public/passport-verification";
    private static final String FILE_MATCH_PATH =
            VERIFICATION_PATH + "/file-match";
    private static final String REFERRER_POLICY_HEADER = "Referrer-Policy";
    private static final String RATE_LIMIT_RESPONSE = """
            {"code":"PUBLIC_RATE_LIMIT_EXCEEDED","message":"잠시 후 다시 시도해 주세요."}
            """;

    private final FixedWindowRequestLimiter verificationLimiter;
    private final FixedWindowRequestLimiter fileMatchLimiter;
    private final TrustedProxyClientIpResolver clientIpResolver;
    private final Duration windowDuration;
    private final Clock clock;

    public PublicVerificationRateLimitFilter(
            int verificationRequestLimit,
            int fileMatchRequestLimit,
            Duration windowDuration,
            TrustedProxyClientIpResolver clientIpResolver,
            Clock clock
    ) {
        this.verificationLimiter = new FixedWindowRequestLimiter(
                verificationRequestLimit,
                windowDuration
        );
        this.fileMatchLimiter = new FixedWindowRequestLimiter(
                fileMatchRequestLimit,
                windowDuration
        );
        this.clientIpResolver = Objects.requireNonNull(clientIpResolver);
        this.windowDuration = windowDuration;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !VERIFICATION_PATH.equals(request.getRequestURI())
                && !FILE_MATCH_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        applyPrivacyHeaders(response);
        if (!tryAcquire(request)) {
            writeRateLimitResponse(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void applyPrivacyHeaders(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(REFERRER_POLICY_HEADER, "no-referrer");
    }

    private boolean tryAcquire(HttpServletRequest request) {
        if (FILE_MATCH_PATH.equals(request.getRequestURI())
                && HttpMethod.POST.matches(request.getMethod())) {
            return fileMatchLimiter.tryAcquire(
                    clientIpResolver.resolve(request),
                    clock.instant()
            );
        }
        if (VERIFICATION_PATH.equals(request.getRequestURI())
                && HttpMethod.GET.matches(request.getMethod())) {
            return verificationLimiter.tryAcquire(
                    clientIpResolver.resolve(request),
                    clock.instant()
            );
        }
        return true;
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setHeader(
                HttpHeaders.RETRY_AFTER,
                Long.toString(windowDuration.toSeconds())
        );
        response.getWriter().write(RATE_LIMIT_RESPONSE.strip());
    }
}
