package com.naesan.passport.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnchorCommitmentCalculatorTest {
    private static final String SNAPSHOT_DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final byte[] ANCHOR_SALT = HexFormat.of().parseHex(
            "000102030405060708090a0b0c0d0e0f"
                    + "101112131415161718191a1b1c1d1e1f"
    );
    private static final String EXPECTED_COMMITMENT =
            "9821426ef5c72b3ce9ec024cd36eacff9710602758972c2387574ed96cf48e4d";

    @Test
    @DisplayName("고정된 ABI encoding으로 salted Keccak-256 commitment를 계산한다")
    void calculatesCommitmentGoldenVector() {
        AnchorCommitment commitment = new AnchorCommitmentCalculator()
                .calculate(SNAPSHOT_DIGEST, ANCHOR_SALT);

        assertThat(commitment.schemaVersion()).isEqualTo(1);
        assertThat(HexFormat.of().formatHex(commitment.anchorSalt()))
                .isEqualTo(HexFormat.of().formatHex(ANCHOR_SALT));
        assertThat(HexFormat.of().formatHex(commitment.commitment()))
                .isEqualTo(EXPECTED_COMMITMENT);
    }

    @Test
    @DisplayName("같은 snapshot도 salt가 다르면 다른 commitment를 만든다")
    void createsDifferentCommitmentForDifferentSalt() {
        AnchorCommitmentCalculator calculator = new AnchorCommitmentCalculator();
        byte[] differentSalt = ANCHOR_SALT.clone();
        differentSalt[0] = 1;

        AnchorCommitment first = calculator.calculate(SNAPSHOT_DIGEST, ANCHOR_SALT);
        AnchorCommitment second = calculator.calculate(SNAPSHOT_DIGEST, differentSalt);

        assertThat(first.commitment()).isNotEqualTo(second.commitment());
    }
}
