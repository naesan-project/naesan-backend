package com.naesan.passport.adapter.out.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.naesan.passport.application.port.out.ProofAnchorCommand;
import com.naesan.passport.application.port.out.ProofFailureType;
import com.naesan.passport.application.port.out.ProofProviderException;
import com.naesan.passport.support.AnvilProofChain;

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
    @DisplayName("zero commitment의 컨트랙트 revert를 영구 실패로 분류한다")
    void classifiesContractRevertAsPermanent() {
        EvmProofAnchorAdapter adapter = adapter(CHAIN_ID, CHAIN.contractAddress(), 1);

        assertThatThrownBy(() -> adapter.submit(command("0".repeat(64))))
                .isInstanceOfSatisfying(ProofProviderException.class, failure -> {
                    assertThat(failure.failureType()).isEqualTo(ProofFailureType.PERMANENT);
                    assertThat(failure.errorCode()).isEqualTo("CONTRACT_REVERT");
                });
    }

    private static EvmProofAnchorAdapter adapter(
            BigInteger chainId,
            String address,
            int confirmations
    ) {
        EvmProofProperties properties = new EvmProofProperties(
                CHAIN.rpcUrl(),
                chainId,
                address,
                CHAIN.deploymentBlock(),
                confirmations,
                3,
                Duration.ZERO
        );
        return new EvmProofAnchorAdapter(
                CHAIN.web3j(),
                CHAIN.writer(),
                properties,
                Clock.systemUTC()
        );
    }

    private static ProofAnchorCommand command(String commitment) {
        return new ProofAnchorCommand("proof-anchor:" + commitment, commitment);
    }
}
