package com.naesan.evidence.application;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

import com.naesan.evidence.domain.EvidenceFile;
import com.naesan.evidence.domain.EvidenceFileState;
import com.naesan.evidence.domain.EvidenceMetadata;
import com.naesan.evidence.domain.EvidenceSnapshot;
import com.naesan.evidence.domain.PurchaseEvidence;

public final class EvidenceSnapshotCanonicalizer {
    private static final int SCHEMA_VERSION = 1;
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final JsonFactory jsonFactory;

    public EvidenceSnapshotCanonicalizer() {
        this.jsonFactory = JsonFactory.builder().build();
    }

    public EvidenceSnapshot createSnapshot(
            PurchaseEvidence evidence,
            EvidenceFile evidenceFile,
            Instant confirmedAt
    ) {
        requirePromotedFile(evidence, evidenceFile);
        Instant canonicalConfirmedAt = confirmedAt.truncatedTo(ChronoUnit.MILLIS);
        byte[] canonicalPayload = canonicalPayload(
                evidence,
                evidenceFile,
                canonicalConfirmedAt
        );
        return new EvidenceSnapshot(
                UUID.randomUUID(),
                evidence.id(),
                SCHEMA_VERSION,
                canonicalPayload,
                sha256(canonicalPayload),
                canonicalConfirmedAt
        );
    }

    private void requirePromotedFile(
            PurchaseEvidence evidence,
            EvidenceFile evidenceFile
    ) {
        if (!evidence.id().equals(evidenceFile.evidenceId())) {
            throw new IllegalArgumentException("Evidence와 파일 식별자가 일치해야 합니다.");
        }
        if (evidenceFile.state() != EvidenceFileState.PROMOTED) {
            throw new IllegalArgumentException("승격된 파일만 snapshot에 포함할 수 있습니다.");
        }
    }

    private byte[] canonicalPayload(
            PurchaseEvidence evidence,
            EvidenceFile evidenceFile,
            Instant confirmedAt
    ) {
        EvidenceMetadata metadata = evidence.metadata();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (JsonGenerator json = jsonFactory.createGenerator(
                ObjectWriteContext.empty(),
                output
        )) {
            json.writeStartObject();
            json.writeNumberProperty("schemaVersion", SCHEMA_VERSION);
            json.writeStringProperty("evidenceId", evidence.id().toString());
            json.writeStringProperty("fileSha256", evidenceFile.sha256());
            json.writeStringProperty("merchantName", metadata.merchantName());
            json.writeStringProperty("productName", metadata.productName());
            writeNullableString(json, "serialNumber", metadata.serialNumber());
            json.writeStringProperty("purchasedAt", metadata.purchasedAt().toString());
            json.writeStringProperty("amount", metadata.amount().toPlainString());
            json.writeStringProperty("currency", metadata.currency());
            json.writeStringProperty(
                    "confirmedAt",
                    DateTimeFormatter.ISO_INSTANT.format(confirmedAt)
            );
            json.writeEndObject();
        }
        return output.toByteArray();
    }

    private void writeNullableString(
            JsonGenerator json,
            String fieldName,
            String value
    ) {
        if (value == null) {
            json.writeNullProperty(fieldName);
            return;
        }
        json.writeStringProperty(fieldName, value);
    }

    private String sha256(byte[] canonicalPayload) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(canonicalPayload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
