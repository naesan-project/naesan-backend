package com.naesan.passport.support;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public final class FaultInjectingJsonRpcProxy implements AutoCloseable {
    private static final Pattern JSON_RPC_METHOD = Pattern.compile(
            "\\\"method\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""
    );

    private final URI upstream;
    private final HttpClient client;
    private final HttpServer server;
    private final ExecutorService executor;
    private final AtomicBoolean truncateRawTransactionResponses = new AtomicBoolean();
    private final AtomicInteger forwardedRawTransactions = new AtomicInteger();
    private final AtomicReference<CountDownLatch> transactionCountResponses =
            new AtomicReference<>();
    private final Map<String, InjectedHttpFailure> injectedHttpFailures =
            new ConcurrentHashMap<>();
    private final Map<String, InjectedDelay> injectedDelays = new ConcurrentHashMap<>();

    public FaultInjectingJsonRpcProxy(URI upstream) {
        this.upstream = upstream;
        executor = Executors.newVirtualThreadPerTaskExecutor();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(executor)
                .build();
        try {
            server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                    0
            );
        } catch (IOException exception) {
            executor.shutdownNow();
            throw new IllegalStateException("JSON-RPC 장애 주입기를 만들지 못했습니다.", exception);
        }
        server.createContext("/", this::forward);
        server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public URI rpcUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    public void truncateRawTransactionResponses() {
        truncateRawTransactionResponses.set(true);
    }

    public void forwardRawTransactionResponses() {
        truncateRawTransactionResponses.set(false);
    }

    public int forwardedRawTransactionCount() {
        return forwardedRawTransactions.get();
    }

    public void synchronizeNextTransactionCountResponses(int parties) {
        if (parties < 2) {
            throw new IllegalArgumentException("동기화할 nonce 응답은 두 개 이상이어야 합니다.");
        }
        if (!transactionCountResponses.compareAndSet(null, new CountDownLatch(parties))) {
            throw new IllegalStateException("이미 nonce 응답 동기화가 진행 중입니다.");
        }
    }

    public void failNextResponses(String method, int statusCode, int count) {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("실패시킬 JSON-RPC method가 필요합니다.");
        }
        if (statusCode < 400 || statusCode > 599 || count < 1) {
            throw new IllegalArgumentException("HTTP 실패 상태와 횟수가 유효하지 않습니다.");
        }
        injectedHttpFailures.put(method, new InjectedHttpFailure(statusCode, count));
    }

    public void delayNextResponses(String method, Duration delay, int count) {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("지연할 JSON-RPC method가 필요합니다.");
        }
        if (delay == null || delay.isZero() || delay.isNegative() || count < 1) {
            throw new IllegalArgumentException("응답 지연 시간과 횟수가 유효하지 않습니다.");
        }
        injectedDelays.put(method, new InjectedDelay(delay, count));
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void forward(HttpExchange exchange) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        String requestJson = new String(requestBody, StandardCharsets.UTF_8);
        var methodMatcher = JSON_RPC_METHOD.matcher(requestJson);
        String method = methodMatcher.find() ? methodMatcher.group(1) : "";
        boolean rawTransaction = "eth_sendRawTransaction".equals(method);
        boolean transactionCount = "eth_getTransactionCount".equals(method);
        if (rawTransaction) {
            forwardedRawTransactions.incrementAndGet();
        }
        InjectedDelay injectedDelay = injectedDelays.get(method);
        if (injectedDelay != null && injectedDelay.consume()) {
            pause(injectedDelay.delay());
        }
        InjectedHttpFailure injectedFailure = injectedHttpFailures.get(method);
        if (injectedFailure != null && injectedFailure.consume()) {
            writeInjectedFailure(exchange, injectedFailure.statusCode());
            return;
        }

        try {
            HttpResponse<byte[]> upstreamResponse = client.send(
                    HttpRequest.newBuilder(upstream)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            if (transactionCount) {
                awaitSynchronizedTransactionCountResponses();
            }
            if (rawTransaction && truncateRawTransactionResponses.get()) {
                truncateResponse(exchange, upstreamResponse);
                return;
            }
            writeResponse(exchange, upstreamResponse);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        }
    }

    private static void pause(Duration delay) throws IOException {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("JSON-RPC 응답 지연이 중단되었습니다.", exception);
        }
    }

    private void awaitSynchronizedTransactionCountResponses() throws IOException {
        CountDownLatch barrier = transactionCountResponses.get();
        if (barrier == null) {
            return;
        }
        barrier.countDown();
        try {
            if (!barrier.await(5, TimeUnit.SECONDS)) {
                throw new IOException("nonce 응답 동기화 시간이 초과되었습니다.");
            }
            transactionCountResponses.compareAndSet(barrier, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("nonce 응답 동기화가 중단되었습니다.", exception);
        }
    }

    private static void truncateResponse(
            HttpExchange exchange,
            HttpResponse<byte[]> upstreamResponse
    ) throws IOException {
        byte[] body = upstreamResponse.body();
        int partialLength = Math.max(1, body.length / 2);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(upstreamResponse.statusCode(), body.length + 16L);
        exchange.getResponseBody().write(body, 0, partialLength);
        exchange.close();
    }

    private static void writeInjectedFailure(HttpExchange exchange, int statusCode)
            throws IOException {
        byte[] body = "{\"error\":\"injected JSON-RPC HTTP failure\"}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void writeResponse(
            HttpExchange exchange,
            HttpResponse<byte[]> upstreamResponse
    ) throws IOException {
        byte[] body = upstreamResponse.body();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(upstreamResponse.statusCode(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static final class InjectedHttpFailure {
        private final int statusCode;
        private final AtomicInteger remaining;

        private InjectedHttpFailure(int statusCode, int count) {
            this.statusCode = statusCode;
            remaining = new AtomicInteger(count);
        }

        int statusCode() {
            return statusCode;
        }

        boolean consume() {
            int current;
            do {
                current = remaining.get();
                if (current == 0) {
                    return false;
                }
            } while (!remaining.compareAndSet(current, current - 1));
            return true;
        }
    }

    private static final class InjectedDelay {
        private final Duration delay;
        private final AtomicInteger remaining;

        private InjectedDelay(Duration delay, int count) {
            this.delay = delay;
            remaining = new AtomicInteger(count);
        }

        Duration delay() {
            return delay;
        }

        boolean consume() {
            int current;
            do {
                current = remaining.get();
                if (current == 0) {
                    return false;
                }
            } while (!remaining.compareAndSet(current, current - 1));
            return true;
        }
    }
}
