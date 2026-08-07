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
    private static final Pattern SEND_RAW_TRANSACTION = Pattern.compile(
            "\\\"method\\\"\\s*:\\s*\\\"eth_sendRawTransaction\\\""
    );
    private static final Pattern GET_TRANSACTION_COUNT = Pattern.compile(
            "\\\"method\\\"\\s*:\\s*\\\"eth_getTransactionCount\\\""
    );

    private final URI upstream;
    private final HttpClient client;
    private final HttpServer server;
    private final ExecutorService executor;
    private final AtomicBoolean truncateRawTransactionResponses = new AtomicBoolean();
    private final AtomicInteger forwardedRawTransactions = new AtomicInteger();
    private final AtomicReference<CountDownLatch> transactionCountResponses =
            new AtomicReference<>();

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

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void forward(HttpExchange exchange) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        String requestJson = new String(requestBody, StandardCharsets.UTF_8);
        boolean rawTransaction = SEND_RAW_TRANSACTION.matcher(requestJson).find();
        boolean transactionCount = GET_TRANSACTION_COUNT.matcher(requestJson).find();
        if (rawTransaction) {
            forwardedRawTransactions.incrementAndGet();
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
}
