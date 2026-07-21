package com.naesan.share.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.naesan.passport.domain.AnchorCommitment;
import com.naesan.passport.domain.AnchorCommitmentCalculator;
import com.naesan.passport.domain.PassportStatus;
import com.naesan.passport.domain.ProofAnchorState;
import com.naesan.share.domain.PublicShare;
import com.naesan.share.domain.PublicShareCapability;

class PublicFileMatchVerifierTest {
    private static final Instant CREATED_AT = Instant.parse("2026-07-21T00:00:00Z");
    private static final byte[] ORIGINAL_FILE =
            "%PDF-1.7\noriginal".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ANCHOR_SALT = new byte[32];

    private final AnchorCommitmentCalculator commitmentCalculator =
            new AnchorCommitmentCalculator();
    private final PublicFileMatchVerifier verifier =
            new PublicFileMatchVerifier(commitmentCalculator, 1024);

    @Test
    @DisplayName("같은 candidate bytes로 snapshot과 commitment를 재계산하면 일치한다")
    void matchesOriginalFile() {
        PublicShareVerificationSource source = verificationSource();

        boolean matched = verifier.matches(
                new ByteArrayInputStream(ORIGINAL_FILE),
                "application/pdf",
                source
        );

        assertThat(matched).isTrue();
    }

    @Test
    @DisplayName("다른 candidate bytes는 확정된 commitment와 일치하지 않는다")
    void rejectsDifferentFile() {
        boolean matched = verifier.matches(
                new ByteArrayInputStream(
                        "%PDF-1.7\ndifferent".getBytes(StandardCharsets.UTF_8)
                ),
                "application/pdf",
                verificationSource()
        );

        assertThat(matched).isFalse();
    }

    @Test
    @DisplayName("선언 형식과 signature가 다르거나 크기 제한을 넘으면 거부한다")
    void rejectsInvalidCandidate() {
        assertThatThrownBy(() -> verifier.matches(
                new ByteArrayInputStream(ORIGINAL_FILE),
                "image/png",
                verificationSource()
        )).isInstanceOfSatisfying(
                PublicShareException.class,
                exception -> assertThat(exception.code())
                        .isEqualTo(PublicShareErrorCode.PUBLIC_FILE_TYPE_MISMATCH)
        );

        PublicFileMatchVerifier smallVerifier =
                new PublicFileMatchVerifier(commitmentCalculator, 5);
        assertThatThrownBy(() -> smallVerifier.matches(
                new ByteArrayInputStream(ORIGINAL_FILE),
                "application/pdf",
                verificationSource()
        )).isInstanceOfSatisfying(
                PublicShareException.class,
                exception -> assertThat(exception.code())
                        .isEqualTo(PublicShareErrorCode.PUBLIC_FILE_TOO_LARGE)
        );
    }

    private PublicShareVerificationSource verificationSource() {
        byte[] canonicalPayload = canonicalPayload(sha256Hex(ORIGINAL_FILE));
        String snapshotDigest = sha256Hex(canonicalPayload);
        AnchorCommitment commitment = commitmentCalculator.calculate(
                snapshotDigest,
                ANCHOR_SALT
        );
        PublicShare publicShare = PublicShare.issue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new byte[32],
                PublicShareCapability.FILE_MATCH,
                CREATED_AT.plus(7, ChronoUnit.DAYS),
                CREATED_AT
        );
        return new PublicShareVerificationSource(
                publicShare,
                "생각등대",
                LocalDate.parse("2026-07-01"),
                PassportStatus.ACTIVE,
                ProofAnchorState.CONFIRMED,
                commitment.commitment(),
                commitment.schemaVersion(),
                snapshotDigest,
                ANCHOR_SALT,
                1,
                canonicalPayload
        );
    }

    private byte[] canonicalPayload(String fileDigest) {
        return ("""
                {"schemaVersion":1,"fileSha256":"%s","productName":"생각등대"}
                """.formatted(fileDigest).strip())
                .getBytes(StandardCharsets.UTF_8);
    }

    private String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
