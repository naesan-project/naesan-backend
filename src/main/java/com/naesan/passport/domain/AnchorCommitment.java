package com.naesan.passport.domain;

public final class AnchorCommitment {
    private static final int HASH_BYTE_LENGTH = 32;

    private final int schemaVersion;
    private final byte[] anchorSalt;
    private final byte[] commitment;

    public AnchorCommitment(
            int schemaVersion,
            byte[] anchorSalt,
            byte[] commitment
    ) {
        validate(schemaVersion, anchorSalt, commitment);
        this.schemaVersion = schemaVersion;
        this.anchorSalt = anchorSalt.clone();
        this.commitment = commitment.clone();
    }

    private static void validate(
            int schemaVersion,
            byte[] anchorSalt,
            byte[] commitment
    ) {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Commitment schema version은 0보다 커야 합니다.");
        }
        if (anchorSalt == null || anchorSalt.length != HASH_BYTE_LENGTH) {
            throw new IllegalArgumentException("Anchor salt는 32 byte여야 합니다.");
        }
        if (commitment == null || commitment.length != HASH_BYTE_LENGTH) {
            throw new IllegalArgumentException("Commitment는 32 byte여야 합니다.");
        }
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public byte[] anchorSalt() {
        return anchorSalt.clone();
    }

    public byte[] commitment() {
        return commitment.clone();
    }
}
