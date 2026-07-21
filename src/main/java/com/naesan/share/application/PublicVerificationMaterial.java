package com.naesan.share.application;

public record PublicVerificationMaterial(
        String snapshotDigest,
        String anchorSalt,
        String commitment,
        int snapshotSchemaVersion,
        int commitmentSchemaVersion,
        String domain,
        String snapshotHashAlgorithm,
        String commitmentHashAlgorithm,
        String commitmentEncoding
) {
}
