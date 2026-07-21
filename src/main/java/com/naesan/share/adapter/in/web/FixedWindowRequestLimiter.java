package com.naesan.share.adapter.in.web;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FixedWindowRequestLimiter {
    private static final int MAXIMUM_CLIENT_COUNT = 10_000;

    private final int requestLimit;
    private final Duration windowDuration;
    private final ConcurrentHashMap<String, ClientWindow> clientWindows;

    public FixedWindowRequestLimiter(int requestLimit, Duration windowDuration) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("요청 제한은 0보다 커야 합니다.");
        }
        if (windowDuration == null
                || windowDuration.isZero()
                || windowDuration.isNegative()) {
            throw new IllegalArgumentException("요청 제한 구간은 0보다 커야 합니다.");
        }
        this.requestLimit = requestLimit;
        this.windowDuration = windowDuration;
        this.clientWindows = new ConcurrentHashMap<>();
    }

    public boolean tryAcquire(String clientKey, Instant requestedAt) {
        removeExpiredClientsWhenFull(requestedAt);
        if (!clientWindows.containsKey(clientKey)
                && clientWindows.size() >= MAXIMUM_CLIENT_COUNT) {
            return false;
        }
        AtomicBoolean acquired = new AtomicBoolean();
        clientWindows.compute(clientKey, (key, currentWindow) -> {
            if (currentWindow == null || currentWindow.isExpiredAt(requestedAt)) {
                acquired.set(true);
                return new ClientWindow(
                        requestedAt.plus(windowDuration),
                        1
                );
            }
            if (currentWindow.requestCount() >= requestLimit) {
                return currentWindow;
            }
            acquired.set(true);
            return currentWindow.increment();
        });
        return acquired.get();
    }

    private void removeExpiredClientsWhenFull(Instant requestedAt) {
        if (clientWindows.size() < MAXIMUM_CLIENT_COUNT) {
            return;
        }
        clientWindows.entrySet()
                .removeIf(entry -> entry.getValue().isExpiredAt(requestedAt));
    }

    private record ClientWindow(
            Instant expiresAt,
            int requestCount
    ) {

        private boolean isExpiredAt(Instant requestedAt) {
            return !requestedAt.isBefore(expiresAt);
        }

        private ClientWindow increment() {
            return new ClientWindow(expiresAt, requestCount + 1);
        }
    }
}
