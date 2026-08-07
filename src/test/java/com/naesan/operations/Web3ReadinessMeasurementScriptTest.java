package com.naesan.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class Web3ReadinessMeasurementScriptTest {
    private static final Path SCRIPT_PATH = Path.of(
            "operations/measure-web3-readiness.sh"
    );

    @TempDir
    Path tempDirectory;

    @Test
    @DisplayName("RPC 장애 phase는 liveness와 readiness 상태만 NDJSON으로 기록한다")
    void recordsOutageWithoutResponseDetails() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health", exchange -> respond(
                exchange,
                200,
                "{\"status\":\"UP\"}"
        ));
        server.createContext("/ready", exchange -> respond(
                exchange,
                503,
                "{\"status\":\"DOWN\",\"errorCode\":\"SECRET\"}"
        ));
        server.start();
        Path output = tempDirectory.resolve("outage.ndjson");

        try {
            Process process = new ProcessBuilder(
                    "sh",
                    SCRIPT_PATH.toString(),
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "outage",
                    output.toString(),
                    "1",
                    "3"
            ).redirectErrorStream(true).start();

            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            assertThat(Files.readString(output))
                    .contains("\"phase\":\"outage\"")
                    .contains("\"liveness\":{\"httpStatus\":200,\"status\":\"UP\"}")
                    .contains("\"readiness\":{\"httpStatus\":503,\"status\":\"DOWN\"}")
                    .doesNotContain("SECRET")
                    .doesNotContain("errorCode")
                    .doesNotContain("127.0.0.1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("credential이 포함될 수 있는 URL은 요청하거나 파일로 기록하지 않는다")
    void rejectsUrlWithSensitiveComponents() throws Exception {
        Path output = tempDirectory.resolve("unsafe.ndjson");
        Process process = new ProcessBuilder(
                "sh",
                SCRIPT_PATH.toString(),
                "https://example.com?token=secret",
                "baseline",
                output.toString(),
                "1",
                "1"
        ).redirectErrorStream(true).start();

        assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isEqualTo(64);
        assertThat(output).doesNotExist();
    }

    private void respond(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json"
        );
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
