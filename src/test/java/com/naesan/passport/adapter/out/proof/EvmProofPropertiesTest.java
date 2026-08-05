package com.naesan.passport.adapter.out.proof;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvmProofPropertiesTest {
    private static final String CONTRACT_ADDRESS =
            "0x0000000000000000000000000000000000000001";
    @Test
    @DisplayName("배포된 EVM provider의 필수 연결 설정을 허용한다")
    void acceptsDeployableConfiguration() {
        assertThatNoException().isThrownBy(() -> properties(
                URI.create("https://sepolia.example.invalid"),
                BigInteger.valueOf(11_155_111L),
                CONTRACT_ADDRESS,
                BigInteger.ZERO,
                2,
                12,
                Duration.ofSeconds(1)
        ));
    }

    @Test
    @DisplayName("확정 수가 0이면 설정을 거절한다")
    void rejectsZeroConfirmations() {
        assertThatIllegalArgumentException().isThrownBy(() -> properties(
                URI.create("http://localhost:8545"),
                BigInteger.valueOf(31_337L),
                CONTRACT_ADDRESS,
                BigInteger.ZERO,
                0,
                1,
                Duration.ZERO
        ));
    }

    @Test
    @DisplayName("writer private key 형식이 잘못되면 credentials 생성을 거절한다")
    void rejectsInvalidPrivateKey() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ProofAdapterConfiguration().evmWriterCredentials("secret")
        );
    }

    private EvmProofProperties properties(
            URI rpcUrl,
            BigInteger chainId,
            String contractAddress,
            BigInteger deploymentBlock,
            int requiredConfirmations,
            int receiptAttempts,
            Duration receiptPollInterval
    ) {
        return new EvmProofProperties(
                rpcUrl,
                chainId,
                contractAddress,
                deploymentBlock,
                requiredConfirmations,
                receiptAttempts,
                receiptPollInterval
        );
    }
}
