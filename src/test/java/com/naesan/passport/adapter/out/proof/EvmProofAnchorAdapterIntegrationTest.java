package com.naesan.passport.adapter.out.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.http.HttpService;

import okhttp3.OkHttpClient;

import com.naesan.passport.application.port.out.ProofAnchorCommand;
import com.naesan.passport.application.port.out.ProofFailureType;
import com.naesan.passport.application.port.out.ProofProviderException;
import com.naesan.passport.support.AnvilProofChain;
import com.naesan.passport.support.FaultInjectingJsonRpcProxy;

@Tag("evm")
class EvmProofAnchorAdapterIntegrationTest {
    private static final BigInteger CHAIN_ID = BigInteger.valueOf(31_337L);
    private static final String COMMITMENT = "a".repeat(64);
    private static final AnvilProofChain CHAIN = new AnvilProofChain(CHAIN_ID);

    @BeforeAll
    static void startChain() {
        CHAIN.start();
    }

    @AfterAll
    static void stopChain() {
        CHAIN.close();
    }

    @Test
    @DisplayName("실제 EVM에 commitment를 제출하고 컨트랙트에서 다시 조회한다")
    void submitsAndReadsBackCommitment() {
        EvmProofAnchorAdapter adapter = adapter(CHAIN_ID, CHAIN.contractAddress(), 1);

        var submitted = adapter.submit(command(COMMITMENT));
        var lookedUp = adapter.lookup(COMMITMENT);

        assertThat(submitted.confirmed()).isTrue();
        assertThat(submitted.externalReference()).startsWith("0x").hasSize(66);
        assertThat(submitted.evidence()).isNotNull();
        assertThat(submitted.evidence().chainId()).isEqualTo(CHAIN_ID);
        assertThat(submitted.evidence().contractAddress())
                .isEqualTo(CHAIN.contractAddress());
        assertThat(submitted.evidence().transactionHash())
                .isEqualTo(submitted.externalReference());
        assertThat(submitted.evidence().readBackCommitment())
                .containsExactly(HexFormat.of().parseHex(COMMITMENT));
        assertThat(lookedUp).get().satisfies(receipt -> {
            assertThat(receipt.externalReference())
                    .isEqualTo(submitted.externalReference());
            assertThat(receipt.anchoredAt()).isEqualTo(submitted.anchoredAt());
            assertThat(receipt.confirmed()).isTrue();
        });
    }

    @Test
    @DisplayName("같은 commitment 재제출은 새 transaction 없이 기존 기준점을 반환한다")
    void deduplicatesSubmissionByLookup() {
        String commitment = "b".repeat(64);
        EvmProofAnchorAdapter adapter = adapter(CHAIN_ID, CHAIN.contractAddress(), 1);

        var first = adapter.submit(command(commitment));
        var second = adapter.submit(command(commitment));

        assertThat(second.externalReference()).isEqualTo(first.externalReference());
        assertThat(second.anchoredAt()).isEqualTo(first.anchoredAt());
    }

    @Test
    @DisplayName("필요한 confirmation 전에는 미확정으로 반환하고 블록 생성 후 확정한다")
    void waitsForRequiredConfirmations() {
        String commitment = "c".repeat(64);
        EvmProofAnchorAdapter adapter = adapter(CHAIN_ID, CHAIN.contractAddress(), 2);

        var submitted = adapter.submit(command(commitment));
        assertThat(submitted.confirmed()).isFalse();

        CHAIN.mine();

        assertThat(adapter.lookup(commitment)).get().extracting("confirmed")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("설정한 chain ID가 RPC와 다르면 transaction 전에 영구 실패한다")
    void rejectsWrongChain() {
        EvmProofAnchorAdapter adapter = adapter(
                BigInteger.valueOf(11_155_111L),
                CHAIN.contractAddress(),
                1
        );

        assertThatThrownBy(() -> adapter.submit(command("d".repeat(64))))
                .isInstanceOfSatisfying(ProofProviderException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProofFailureType.PERMANENT);
                    assertThat(failure.errorCode()).isEqualTo("CHAIN_ID_MISMATCH");
                });
    }

    @Test
    @DisplayName("배포 코드가 없는 주소는 transaction 전에 영구 실패한다")
    void rejectsMissingContract() {
        EvmProofAnchorAdapter adapter = adapter(
                CHAIN_ID,
                "0x0000000000000000000000000000000000000001",
                1
        );

        assertThatThrownBy(() -> adapter.submit(command("e".repeat(64))))
                .isInstanceOfSatisfying(ProofProviderException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProofFailureType.PERMANENT);
                    assertThat(failure.errorCode()).isEqualTo("CONTRACT_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("서명 계정이 컨트랙트 writer와 다르면 transaction 전에 영구 실패한다")
    void rejectsWrongWriter() {
        EvmProofProperties properties = properties(
                CHAIN_ID,
                CHAIN.contractAddress(),
                1
        );
        EvmProofAnchorAdapter adapter = new EvmProofAnchorAdapter(
                CHAIN.web3j(),
                Credentials.create("1".repeat(64)),
                properties,
                Clock.systemUTC()
        );

        assertThatThrownBy(() -> adapter.submit(command("f".repeat(64))))
                .isInstanceOfSatisfying(ProofProviderException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProofFailureType.PERMANENT);
                    assertThat(failure.errorCode()).isEqualTo("WRITER_MISMATCH");
                });
    }

    @Test
    @DisplayName("zero commitment의 컨트랙트 revert를 영구 실패로 분류한다")
    void classifiesContractRevertAsPermanent() {
        EvmProofAnchorAdapter adapter = adapter(CHAIN_ID, CHAIN.contractAddress(), 1);

        assertThatThrownBy(() -> adapter.submit(command("0".repeat(64))))
                .isInstanceOfSatisfying(ProofProviderException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProofFailureType.PERMANENT);
                    assertThat(failure.errorCode()).isEqualTo("CONTRACT_REVERT");
                });
    }

    @Test
    @DisplayName("transaction 전파 후 응답이 유실되면 체인 조회로 한 번만 제출된 기준점을 복구한다")
    void recoversBroadcastTransactionAfterResponseLoss() {
        String commitment = "1".repeat(64);
        try (var proxy = new FaultInjectingJsonRpcProxy(CHAIN.rpcUrl())) {
            proxy.start();
            proxy.truncateRawTransactionResponses();
            Web3j proxiedWeb3j = Web3j.build(new HttpService(proxy.rpcUrl().toString()));
            EvmProofAnchorAdapter adapter = adapter(
                    proxiedWeb3j,
                    CHAIN_ID,
                    CHAIN.contractAddress(),
                    1
            );
            try {
                assertThatThrownBy(() -> adapter.submit(command(commitment)))
                        .isInstanceOfSatisfying(ProofProviderException.class, failure -> {
                            assertThat(failure.failureType())
                                    .isEqualTo(ProofFailureType.AMBIGUOUS);
                            assertThat(failure.errorCode())
                                    .isEqualTo("SUBMIT_RESULT_UNKNOWN");
                        });

                proxy.forwardRawTransactionResponses();
                var recovered = adapter.lookup(commitment);
                int forwardedBeforeDeduplication = proxy.forwardedRawTransactionCount();
                var deduplicated = adapter.submit(command(commitment));

                assertThat(recovered).isPresent();
                assertThat(deduplicated.externalReference())
                        .isEqualTo(recovered.orElseThrow().externalReference());
                assertThat(proxy.forwardedRawTransactionCount())
                        .isEqualTo(forwardedBeforeDeduplication);
                assertThat(anchorEventCount(commitment)).isEqualTo(1);
            } finally {
                proxiedWeb3j.shutdown();
            }
        }
    }

    @Test
    @DisplayName("독립 worker의 nonce 경쟁을 100회 복구해 모든 기준점을 각각 확정한다")
    void retriesConcurrentSubmissionsAfterWriterNonceRace() {
        try (var proxy = new FaultInjectingJsonRpcProxy(CHAIN.rpcUrl())) {
            proxy.start();
            Web3j firstWeb3j = Web3j.build(new HttpService(proxy.rpcUrl().toString()));
            Web3j secondWeb3j = Web3j.build(new HttpService(proxy.rpcUrl().toString()));
            EvmProofAnchorAdapter firstAdapter = adapter(
                    firstWeb3j,
                    CHAIN_ID,
                    CHAIN.contractAddress(),
                    1
            );
            EvmProofAnchorAdapter secondAdapter = adapter(
                    secondWeb3j,
                    CHAIN_ID,
                    CHAIN.contractAddress(),
                    1
            );
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                for (int iteration = 0; iteration < 100; iteration++) {
                    String firstCommitment = commitment(10_000 + iteration * 2);
                    String secondCommitment = commitment(10_001 + iteration * 2);
                    proxy.synchronizeNextTransactionCountResponses(2);
                    var first = CompletableFuture.supplyAsync(
                            () -> submit(firstAdapter, firstCommitment),
                            workers
                    );
                    var second = CompletableFuture.supplyAsync(
                            () -> submit(secondAdapter, secondCommitment),
                            workers
                    );
                    List<SubmissionResult> initial = List.of(first.join(), second.join());

                    assertThat(initial).filteredOn(SubmissionResult::succeeded).hasSize(1);
                    assertThat(initial).filteredOn(result -> !result.succeeded())
                            .singleElement()
                            .satisfies(result -> {
                                assertThat(result.failure().failureType())
                                        .isEqualTo(ProofFailureType.RETRYABLE);
                                assertThat(result.failure().errorCode())
                                        .isEqualTo("NONCE_CONFLICT");
                            });

                    SubmissionResult failed = initial.stream()
                            .filter(result -> !result.succeeded())
                            .findFirst()
                            .orElseThrow();
                    EvmProofAnchorAdapter retryAdapter = failed.commitment()
                            .equals(firstCommitment) ? firstAdapter : secondAdapter;
                    var retried = retryAdapter.submit(command(failed.commitment()));

                    assertThat(retried.confirmed()).isTrue();
                    assertThat(anchorEventCount(firstCommitment)).isEqualTo(1);
                    assertThat(anchorEventCount(secondCommitment)).isEqualTo(1);
                }
            } finally {
                workers.shutdownNow();
                firstWeb3j.shutdown();
                secondWeb3j.shutdown();
            }
        }
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {429, 503})
    @DisplayName("RPC의 일시적인 HTTP 실패는 안전한 재시도 오류로 변환한다")
    void classifiesTransientRpcHttpFailureAsRetryable(int statusCode) {
        try (var proxy = new FaultInjectingJsonRpcProxy(CHAIN.rpcUrl())) {
            proxy.start();
            proxy.failNextResponses("eth_chainId", statusCode, 1);
            Web3j proxiedWeb3j = Web3j.build(new HttpService(proxy.rpcUrl().toString()));
            EvmProofAnchorAdapter adapter = adapter(
                    proxiedWeb3j,
                    CHAIN_ID,
                    CHAIN.contractAddress(),
                    1
            );
            try {
                assertThatThrownBy(() -> adapter.submit(command(commitment(statusCode))))
                        .isInstanceOfSatisfying(ProofProviderException.class, failure -> {
                            assertThat(failure.failureType())
                                    .isEqualTo(ProofFailureType.RETRYABLE);
                            assertThat(failure.errorCode()).isEqualTo("RPC_UNAVAILABLE");
                        });
            } finally {
                proxiedWeb3j.shutdown();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "eth_chainId",
            "eth_getCode",
            "eth_call",
            "eth_getTransactionCount",
            "eth_gasPrice",
            "eth_estimateGas",
            "eth_sendRawTransaction"
    })
    @DisplayName("제출 단계별 HTTP 실패는 Worker가 처리할 수 있는 RPC 오류다")
    void classifiesHttpFailureAcrossSubmissionStages(String method) {
        try (var proxy = new FaultInjectingJsonRpcProxy(CHAIN.rpcUrl())) {
            proxy.start();
            proxy.failNextResponses(method, 503, 1);
            Web3j proxiedWeb3j = Web3j.build(new HttpService(proxy.rpcUrl().toString()));
            EvmProofAnchorAdapter adapter = adapter(
                    proxiedWeb3j,
                    CHAIN_ID,
                    CHAIN.contractAddress(),
                    1
            );
            try {
                assertThatThrownBy(() -> adapter.submit(command(
                        commitment(Math.abs(method.hashCode()))
                )))
                        .isInstanceOfSatisfying(ProofProviderException.class, failure -> {
                            assertThat(failure.failureType())
                                    .isEqualTo(ProofFailureType.RETRYABLE);
                            assertThat(failure.errorCode()).isEqualTo("RPC_UNAVAILABLE");
                        });
            } finally {
                proxiedWeb3j.shutdown();
            }
        }
    }

    @Test
    @DisplayName("receipt 조회 HTTP 실패 뒤에는 기존 transaction을 lookup으로 복구한다")
    void recoversTransactionAfterReceiptHttpFailure() {
        String commitment = commitment(50_000);
        try (var proxy = new FaultInjectingJsonRpcProxy(CHAIN.rpcUrl())) {
            proxy.start();
            proxy.failNextResponses("eth_getTransactionReceipt", 503, 1);
            Web3j proxiedWeb3j = Web3j.build(new HttpService(proxy.rpcUrl().toString()));
            EvmProofAnchorAdapter adapter = adapter(
                    proxiedWeb3j,
                    CHAIN_ID,
                    CHAIN.contractAddress(),
                    1
            );
            try {
                assertThatThrownBy(() -> adapter.submit(command(commitment)))
                        .isInstanceOfSatisfying(ProofProviderException.class, failure -> {
                            assertThat(failure.failureType())
                                    .isEqualTo(ProofFailureType.RETRYABLE);
                            assertThat(failure.errorCode()).isEqualTo("RPC_UNAVAILABLE");
                        });

                assertThat(adapter.lookup(commitment)).isPresent();
                assertThat(anchorEventCount(commitment)).isEqualTo(1);
            } finally {
                proxiedWeb3j.shutdown();
            }
        }
    }

    @Test
    @DisplayName("RPC timeout은 Worker가 처리할 수 있는 재시도 오류다")
    void classifiesRpcTimeoutAsRetryable() {
        try (var proxy = new FaultInjectingJsonRpcProxy(CHAIN.rpcUrl())) {
            proxy.start();
            proxy.delayNextResponses("eth_chainId", Duration.ofMillis(500), 1);
            OkHttpClient client = HttpService.getOkHttpClientBuilder()
                    .readTimeout(Duration.ofMillis(100))
                    .build();
            Web3j proxiedWeb3j = Web3j.build(
                    new HttpService(proxy.rpcUrl().toString(), client)
            );
            EvmProofAnchorAdapter adapter = adapter(
                    proxiedWeb3j,
                    CHAIN_ID,
                    CHAIN.contractAddress(),
                    1
            );
            try {
                assertThatThrownBy(() -> adapter.submit(command(commitment(60_000))))
                        .isInstanceOfSatisfying(ProofProviderException.class, failure -> {
                            assertThat(failure.failureType())
                                    .isEqualTo(ProofFailureType.RETRYABLE);
                            assertThat(failure.errorCode()).isEqualTo("RPC_UNAVAILABLE");
                        });
            } finally {
                proxiedWeb3j.shutdown();
            }
        }
    }

    @Test
    @DisplayName("확정 block이 reorg로 사라지면 lookup에서 부재를 확인하고 다시 제출한다")
    void resubmitsCommitmentAfterConfirmedBlockReorganization() {
        String commitment = commitment(70_000);
        String snapshot = CHAIN.snapshot();
        EvmProofAnchorAdapter adapter = adapter(CHAIN_ID, CHAIN.contractAddress(), 1);

        var initiallyConfirmed = adapter.submit(command(commitment));
        CHAIN.revert(snapshot);

        assertThat(initiallyConfirmed.confirmed()).isTrue();
        assertThat(adapter.lookup(commitment)).isEmpty();

        var resubmitted = adapter.submit(command(commitment));

        assertThat(resubmitted.confirmed()).isTrue();
        assertThat(adapter.lookup(commitment)).isPresent();
        assertThat(anchorEventCount(commitment)).isEqualTo(1);
    }

    private static SubmissionResult submit(
            EvmProofAnchorAdapter adapter,
            String commitment
    ) {
        try {
            adapter.submit(command(commitment));
            return new SubmissionResult(commitment, null);
        } catch (ProofProviderException failure) {
            return new SubmissionResult(commitment, failure);
        }
    }

    private static String commitment(int value) {
        String hex = Integer.toHexString(value);
        return "0".repeat(64 - hex.length()) + hex;
    }

    private static int anchorEventCount(String commitment) {
        Event anchored = new Event(
                "CommitmentAnchored",
                List.of(
                        new TypeReference<Bytes32>(true) { },
                        new TypeReference<Uint64>() { }
                )
        );
        EthFilter filter = new EthFilter(
                new DefaultBlockParameterNumber(CHAIN.deploymentBlock()),
                DefaultBlockParameterName.LATEST,
                CHAIN.contractAddress()
        );
        filter.addSingleTopic(EventEncoder.encode(anchored));
        filter.addSingleTopic("0x" + commitment);
        try {
            return CHAIN.web3j().ethGetLogs(filter).send().getLogs().size();
        } catch (Exception exception) {
            throw new IllegalStateException("anchor event를 조회하지 못했습니다.", exception);
        }
    }

    private static EvmProofAnchorAdapter adapter(
            BigInteger chainId,
            String address,
            int confirmations
    ) {
        return adapter(CHAIN.web3j(), chainId, address, confirmations);
    }

    private static EvmProofAnchorAdapter adapter(
            Web3j web3j,
            BigInteger chainId,
            String address,
            int confirmations
    ) {
        return new EvmProofAnchorAdapter(
                web3j,
                CHAIN.writer(),
                properties(chainId, address, confirmations),
                Clock.systemUTC()
        );
    }

    private static EvmProofProperties properties(
            BigInteger chainId,
            String address,
            int confirmations
    ) {
        return new EvmProofProperties(
                CHAIN.rpcUrl(),
                chainId,
                address,
                CHAIN.deploymentBlock(),
                confirmations,
                12,
                Duration.ofMillis(100)
        );
    }

    private static ProofAnchorCommand command(String commitment) {
        return new ProofAnchorCommand("proof-anchor:" + commitment, commitment);
    }

    private record SubmissionResult(
            String commitment,
            ProofProviderException failure
    ) {

        boolean succeeded() {
            return failure == null;
        }
    }
}
