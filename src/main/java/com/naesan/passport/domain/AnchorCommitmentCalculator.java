package com.naesan.passport.domain;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public final class AnchorCommitmentCalculator {
    private static final int SCHEMA_VERSION = 1;
    private static final int HASH_BYTE_LENGTH = 32;
    private static final int ABI_WORD_BYTE_LENGTH = 32;
    private static final byte[] DOMAIN_TAG =
            Keccak256.digest("NAESAN_ANCHOR".getBytes(StandardCharsets.UTF_8));

    public AnchorCommitment calculate(String snapshotDigest, byte[] anchorSalt) {
        byte[] snapshotDigestBytes = decodeSnapshotDigest(snapshotDigest);
        validateAnchorSalt(anchorSalt);

        byte[] encodedCommitmentInput = new byte[ABI_WORD_BYTE_LENGTH * 4];
        System.arraycopy(DOMAIN_TAG, 0, encodedCommitmentInput, 0, HASH_BYTE_LENGTH);
        encodedCommitmentInput[ABI_WORD_BYTE_LENGTH * 2 - 1] = SCHEMA_VERSION;
        System.arraycopy(
                snapshotDigestBytes,
                0,
                encodedCommitmentInput,
                ABI_WORD_BYTE_LENGTH * 2,
                HASH_BYTE_LENGTH
        );
        System.arraycopy(
                anchorSalt,
                0,
                encodedCommitmentInput,
                ABI_WORD_BYTE_LENGTH * 3,
                HASH_BYTE_LENGTH
        );

        return new AnchorCommitment(
                SCHEMA_VERSION,
                anchorSalt,
                Keccak256.digest(encodedCommitmentInput)
        );
    }

    private byte[] decodeSnapshotDigest(String snapshotDigest) {
        if (snapshotDigest == null || snapshotDigest.length() != HASH_BYTE_LENGTH * 2) {
            throw new IllegalArgumentException("Snapshot digest는 64자 16진수여야 합니다.");
        }
        try {
            return HexFormat.of().parseHex(snapshotDigest);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Snapshot digest는 64자 16진수여야 합니다.");
        }
    }

    private void validateAnchorSalt(byte[] anchorSalt) {
        if (anchorSalt == null || anchorSalt.length != HASH_BYTE_LENGTH) {
            throw new IllegalArgumentException("Anchor salt는 32 byte여야 합니다.");
        }
    }
}
