package com.naesan.passport.support;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public final class FaultInjectingJsonRpcProxy implements AutoCloseable {
    private static final Pattern SEND_RAW_TRANSACTION = Pattern.compile(
            "\\\"method\\\"\\s*:\\s*\\\"eth_sendRawTransaction\\\""
    );

    private final URI upstream;
    private final HttpClient client;
    private final HttpServer server;
    private final ExecutorService executor;
    private final AtomicBoolean truncateRawTransactionResponses = new AtomicBoolean();
    private final AtomicInteger forwardedRawTransactions = new AtomicInteger();

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

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void forward(HttpExchange exchange) throws IOException {
        byte[] requestBody = exchange.getRequestBody().readAllBytes();
        boolean rawTransaction = SEND_RAW_TRANSACTION.matcher(
                new String(requestBody, java.nio.charset.StandardCharsets.UTF_8)
        ).find();
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
