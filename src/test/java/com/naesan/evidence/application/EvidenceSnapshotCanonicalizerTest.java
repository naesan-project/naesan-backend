package com.naesan.evidence.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.naesan.evidence.domain.EvidenceFile;
import com.naesan.evidence.domain.EvidenceFileType;
import com.naesan.evidence.domain.EvidenceMetadata;
import com.naesan.evidence.domain.EvidenceSnapshot;
import com.naesan.evidence.domain.PurchaseEvidence;
import com.naesan.evidence.domain.StorageKey;

class EvidenceSnapshotCanonicalizerTest {
    private static final UUID EVIDENCE_ID =
            UUID.fromString("018f3f42-7600-7b27-9c8b-48f53255b71c");
    private static final String FILE_SHA256 =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Instant CONFIRMED_AT =
            Instant.parse("2026-07-14T09:30:15.123456Z");
    private static final String EXPECTED_JSON = """
            {"schemaVersion":1,"evidenceId":"018f3f42-7600-7b27-9c8b-48f53255b71c","fileSha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","merchantName":"Apple Store","productName":"AirPods Pro","serialNumber":null,"purchasedAt":"2026-06-01","amount":"359000.00","currency":"KRW","confirmedAt":"2026-07-14T09:30:15.123Z"}""";
    private static final String EXPECTED_DIGEST =
            "61d9be80eea6b2f95e0b5ad08d363ec4db73662083bbd43e98e1f9dbe4311647";

    @Test
    @DisplayName("v1 golden fixture와 같은 canonical bytes와 digest를 생성한다")
    void createsGoldenSnapshot() {
        EvidenceSnapshotCanonicalizer canonicalizer =
                new EvidenceSnapshotCanonicalizer();

        EvidenceSnapshot snapshot = canonicalizer.createSnapshot(
                evidence(),
                promotedFile(),
                CONFIRMED_AT
        );

        assertThat(new String(snapshot.canonicalPayload(), StandardCharsets.UTF_8))
                .isEqualTo(EXPECTED_JSON);
        assertThat(snapshot.snapshotDigest()).isEqualTo(EXPECTED_DIGEST);
        assertThat(snapshot.createdAt())
                .isEqualTo(Instant.parse("2026-07-14T09:30:15.123Z"));
    }

    private PurchaseEvidence evidence() {
        return PurchaseEvidence.createDraft(
                        EVIDENCE_ID,
                        UUID.randomUUID(),
                        new EvidenceMetadata(
                                "Apple Store",
                                "AirPods Pro",
                                null,
                                LocalDate.parse("2026-06-01"),
                                new BigDecimal("359000.00"),
                                "KRW"
                        ),
                        Instant.parse("2026-07-01T00:00:00Z")
                )
                .attachFile(Instant.parse("2026-07-02T00:00:00Z"));
    }

    private EvidenceFile promotedFile() {
        return EvidenceFile.createTemporary(
                        UUID.randomUUID(),
                        EVIDENCE_ID,
                        new StorageKey("temporary/file"),
                        FILE_SHA256,
                        EvidenceFileType.PDF,
                        1024,
                        Instant.parse("2026-07-02T00:00:00Z")
                )
                .promote(
                        new StorageKey("permanent/file"),
                        Instant.parse("2026-07-03T00:00:00Z")
                );
    }
}
