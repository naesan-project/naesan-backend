package com.naesan.passport.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.naesan.passport.domain.EvmAnchorEvidence;
import com.naesan.passport.domain.ProofAnchor;
import com.naesan.passport.domain.ProofAnchorState;

class EvmProofResponseTest {

    @Test
    @DisplayName("체인 증거의 큰 숫자와 read-back commitment를 손실 없이 응답한다")
    void mapsEvidenceWithoutJavascriptNumberLoss() {
        Instant anchoredAt = Instant.parse("2026-08-05T00:00:00Z");
        byte[] commitment = HexFormat.of().parseHex("ab".repeat(32));
        var evidence = new EvmAnchorEvidence(
                new BigInteger("11155111"),
                "0x" + "1".repeat(40),
                "0x" + "2".repeat(64),
                new BigInteger("9007199254740993"),
                "0x" + "3".repeat(64),
                3,
                commitment,
                anchoredAt.plusSeconds(30)
        );
        ProofAnchor proofAnchor = ProofAnchor.restore(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                new byte[32],
                commitment,
                ProofAnchorState.CONFIRMED,
                evidence.transactionHash(),
                evidence,
                anchoredAt.minusSeconds(10),
                anchoredAt
        );

        EvmProofResponse response = EvmProofResponse.from(proofAnchor);

        assertThat(response.chainId()).isEqualTo("11155111");
        assertThat(response.blockNumber()).isEqualTo("9007199254740993");
        assertThat(response.readBackCommitment()).isEqualTo("ab".repeat(32));
        assertThat(response.anchoredAt()).isEqualTo(anchoredAt);
    }
}
