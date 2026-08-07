package com.naesan.passport.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.naesan.TestcontainersConfiguration;
import com.naesan.passport.application.IssuePassportService;
import com.naesan.passport.application.ProcessProofOutboxService;
import com.naesan.passport.application.port.out.ProofAnchorPort;
import com.naesan.passport.support.AnvilProofChain;
import com.naesan.passport.support.FaultInjectingJsonRpcProxy;

@Tag("evm")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EvmProofOutboxIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("a848616d-32d3-49b3-9d99-a65ecab0835b");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("ae6a67f6-a6fa-4661-8c91-5c38f61d6ef3");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("aed04a73-e85d-46ef-8c4a-b8ece309ef28");
    private static final Instant CREATED_AT = Instant.parse("2026-08-05T00:00:00Z");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final AnvilProofChain CHAIN = new AnvilProofChain(
            BigInteger.valueOf(31_337L)
    );
    private static final FaultInjectingJsonRpcProxy PROXY;

    static {
        CHAIN.start();
        PROXY = new FaultInjectingJsonRpcProxy(CHAIN.rpcUrl());
        PROXY.start();
    }

    private final IssuePassportService issuePassportService;
    private final ProcessProofOutboxService processProofOutboxService;
    private final ProofAnchorPort proofAnchorPort;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EvmProofOutboxIntegrationTest(
            IssuePassportService issuePassportService,
            ProcessProofOutboxService processProofOutboxService,
            ProofAnchorPort proofAnchorPort,
            JdbcTemplate jdbcTemplate
    ) {
        this.issuePassportService = issuePassportService;
        this.processProofOutboxService = processProofOutboxService;
        this.proofAnchorPort = proofAnchorPort;
        this.jdbcTemplate = jdbcTemplate;
    }

    @DynamicPropertySource
    static void configureEvm(DynamicPropertyRegistry registry) {
        registry.add("naesan.proof.provider", () -> "evm");
        registry.add("naesan.proof.worker.enabled", () -> "true");
        registry.add("naesan.proof.evm.rpc-url", () -> PROXY.rpcUrl().toString());
        registry.add("naesan.proof.evm.chain-id", () -> CHAIN.chainId().toString());
        registry.add("naesan.proof.evm.contract-address", CHAIN::contractAddress);
        registry.add("naesan.proof.evm.private-key", CHAIN::writerPrivateKey);
        registry.add(
                "naesan.proof.evm.deployment-block",
                () -> CHAIN.deploymentBlock().toString()
        );
        registry.add("naesan.proof.evm.required-confirmations", () -> "1");
        registry.add("naesan.proof.evm.receipt-attempts", () -> "3");
        registry.add("naesan.proof.evm.receipt-poll-interval", () -> "0s");
    }

    @AfterAll
    static void stopChain() {
        PROXY.close();
        CHAIN.close();
    }

    @BeforeEach
    void prepareIssuedPassport() {
        PROXY.forwardRawTransactionResponses();
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM proof_anchors");
        jdbcTemplate.update("DELETE FROM ownership_history");
        jdbcTemplate.update("DELETE FROM passports");
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'evm-worker@example.com', ?, 'ACTIVE', ?)
                """,
                OWNER_ACCOUNT_ID,
                BCRYPT_HASH,
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name, purchased_at,
                    amount, currency, state, version, created_at, updated_at, confirmed_at
                )
                VALUES (
                    ?, ?, '생각상점', '생각등대', ?,
                    ?, 'KRW', 'CONFIRMED', 1, ?, ?, ?
                )
                """,
                EVIDENCE_ID,
                OWNER_ACCOUNT_ID,
                LocalDate.parse("2026-08-05"),
                new BigDecimal("1000.00"),
                CREATED_AT.atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
        jdbcTemplate.update(
                """
                INSERT INTO evidence_snapshots (
                    id, evidence_id, schema_version, canonical_payload,
                    snapshot_digest, created_at
                )
                VALUES (?, ?, 1, ?, ?, ?)
                """,
                SNAPSHOT_ID,
                EVIDENCE_ID,
                "{}".getBytes(StandardCharsets.UTF_8),
                "a".repeat(64),
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
        issuePassportService.issue(OWNER_ACCOUNT_ID, SNAPSHOT_ID);
    }

    @Test
    @DisplayName("패스 proof outbox를 실제 EVM에 제출하고 체인 증거와 함께 확정한다")
    void confirmsProofThroughRealEvmProvider() {
        boolean processed = processProofOutboxService.processNext("evm-worker");

        assertThat(processed).isTrue();
        var proof = jdbcTemplate.queryForMap("""
                SELECT
                    state,
                    commitment,
                    chain_id,
                    contract_address,
                    transaction_hash,
                    block_hash,
                    confirmation_count,
                    read_back_commitment
                FROM proof_anchors
                """);
        assertThat(proof.get("state")).isEqualTo("CONFIRMED");
        assertThat(proof.get("chain_id")).isEqualTo(new BigDecimal("31337"));
        assertThat(proof.get("contract_address")).isEqualTo(CHAIN.contractAddress());
        assertThat(proof.get("transaction_hash").toString()).startsWith("0x");
        assertThat(proof.get("block_hash").toString()).startsWith("0x");
        assertThat(proof.get("confirmation_count")).isEqualTo(1);
        assertThat((byte[]) proof.get("read_back_commitment"))
                .containsExactly((byte[]) proof.get("commitment"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events",
                String.class
        )).isEqualTo("SUCCEEDED");

        String commitment = HexFormat.of().formatHex((byte[]) proof.get("commitment"));
        assertThat(proofAnchorPort.lookup(commitment)).get().satisfies(receipt -> {
            assertThat(receipt.confirmed()).isTrue();
            assertThat(receipt.externalReference())
                    .isEqualTo(proof.get("transaction_hash"));
        });
    }

    @Test
    @DisplayName("전파 후 응답을 잃은 proof는 재제출하지 않고 체인 조회로 확정한다")
    void reconcilesBroadcastProofAfterResponseLoss() {
        PROXY.truncateRawTransactionResponses();

        boolean submitted = processProofOutboxService.processNext("evm-worker-submit");

        assertThat(submitted).isTrue();
        assertThat(jdbcTemplate.queryForMap("""
                SELECT status, error_category, error_code
                FROM outbox_events
                """))
                .containsEntry("status", "RECONCILE_PENDING")
                .containsEntry("error_category", "AMBIGUOUS")
                .containsEntry("error_code", "SUBMIT_RESULT_UNKNOWN");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT state FROM proof_anchors",
                String.class
        )).isEqualTo("RECONCILE_PENDING");
        int forwardedAfterUnknownResult = PROXY.forwardedRawTransactionCount();

        PROXY.forwardRawTransactionResponses();
        boolean reconciled = processProofOutboxService.processNext("evm-worker-reconcile");

        assertThat(reconciled).isTrue();
        assertThat(PROXY.forwardedRawTransactionCount())
                .isEqualTo(forwardedAfterUnknownResult);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT status, attempt_count
                FROM outbox_events
                """))
                .containsEntry("status", "SUCCEEDED")
                .containsEntry("attempt_count", 2);
        var proof = jdbcTemplate.queryForMap("""
                SELECT state, transaction_hash, block_hash, confirmation_count
                FROM proof_anchors
                """);
        assertThat(proof)
                .containsEntry("state", "CONFIRMED")
                .containsEntry("confirmation_count", 1);
        assertThat(proof.get("transaction_hash").toString()).startsWith("0x");
        assertThat(proof.get("block_hash").toString()).startsWith("0x");
    }

    @Test
    @DisplayName("RPC rate limit은 Worker를 종료하지 않고 Outbox 재시도로 전환한다")
    void schedulesRetryAfterRpcRateLimit() {
        PROXY.failNextResponses("eth_chainId", 429, 1);

        boolean processed = processProofOutboxService.processNext("evm-worker-rate-limit");

        assertThat(processed).isTrue();
        assertThat(jdbcTemplate.queryForMap("""
                SELECT status, error_category, error_code
                FROM outbox_events
                """))
                .containsEntry("status", "RETRY_WAIT")
                .containsEntry("error_category", "RETRYABLE")
                .containsEntry("error_code", "RPC_UNAVAILABLE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT state FROM proof_anchors",
                String.class
        )).isEqualTo("PREPARED");
    }
}
