package com.naesan.share.application;

import java.time.LocalDate;
import java.util.Objects;

import com.naesan.passport.domain.PassportStatus;
import com.naesan.passport.domain.ProofAnchorState;
import com.naesan.share.domain.PublicShare;

public final class PublicShareVerificationSource {
    private final PublicShare publicShare;
    private final String productName;
    private final LocalDate purchasedAt;
    private final PassportStatus passportStatus;
    private final ProofAnchorState proofState;
    private final byte[] commitment;
    private final int commitmentSchemaVersion;
    private final String snapshotDigest;
    private final byte[] anchorSalt;
    private final int snapshotSchemaVersion;
    private final byte[] canonicalPayload;

    public PublicShareVerificationSource(
            PublicShare publicShare,
            String productName,
            LocalDate purchasedAt,
            PassportStatus passportStatus,
            ProofAnchorState proofState,
            byte[] commitment,
            int commitmentSchemaVersion,
            String snapshotDigest,
            byte[] anchorSalt,
            int snapshotSchemaVersion,
            byte[] canonicalPayload
    ) {
        this.publicShare = Objects.requireNonNull(publicShare);
        this.productName = Objects.requireNonNull(productName);
        this.purchasedAt = Objects.requireNonNull(purchasedAt);
        this.passportStatus = Objects.requireNonNull(passportStatus);
        this.proofState = Objects.requireNonNull(proofState);
        this.commitment = Objects.requireNonNull(commitment).clone();
        this.commitmentSchemaVersion = commitmentSchemaVersion;
        this.snapshotDigest = Objects.requireNonNull(snapshotDigest);
        this.anchorSalt = Objects.requireNonNull(anchorSalt).clone();
        this.snapshotSchemaVersion = snapshotSchemaVersion;
        this.canonicalPayload = Objects.requireNonNull(canonicalPayload).clone();
    }

    public PublicShare publicShare() {
        return publicShare;
    }

    public String productName() {
        return productName;
    }

    public LocalDate purchasedAt() {
        return purchasedAt;
    }

    public PassportStatus passportStatus() {
        return passportStatus;
    }

    public ProofAnchorState proofState() {
        return proofState;
    }

    public byte[] commitment() {
        return commitment.clone();
    }

    public int commitmentSchemaVersion() {
        return commitmentSchemaVersion;
    }

    public String snapshotDigest() {
        return snapshotDigest;
    }

    public byte[] anchorSalt() {
        return anchorSalt.clone();
    }

    public int snapshotSchemaVersion() {
        return snapshotSchemaVersion;
    }

    public byte[] canonicalPayload() {
        return canonicalPayload.clone();
    }
}
