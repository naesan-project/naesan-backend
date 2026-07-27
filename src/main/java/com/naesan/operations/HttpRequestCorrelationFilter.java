package com.naesan.operations;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpRequestCorrelationFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_MDC_KEY = "request_id";
    private static final String ACTUATOR_PATH_PREFIX = "/actuator/";
    private static final Logger LOGGER =
            LoggerFactory.getLogger(HttpRequestCorrelationFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try (MDC.MDCCloseable ignored = MDC.putCloseable(
                REQUEST_ID_MDC_KEY,
                requestId
        )) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                logCompletedRequest(request, response, startedAt);
            }
        }
    }

    private void logCompletedRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            long startedAt
    ) {
        long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
        LOGGER.atInfo()
                .addKeyValue("event", "http_request_completed")
                .addKeyValue("method", request.getMethod())
                .addKeyValue("status", response.getStatus())
                .addKeyValue("duration_ms", durationMillis)
                .log("HTTP request completed");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/health")
                || path.equals("/ready")
                || path.startsWith(ACTUATOR_PATH_PREFIX);
    }
}
