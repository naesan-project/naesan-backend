package com.naesan.passport.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.naesan.evidence.application.port.out.EvidenceSnapshotRepository;
import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.evidence.domain.EvidenceMetadata;
import com.naesan.evidence.domain.EvidenceSnapshot;
import com.naesan.evidence.domain.PurchaseEvidence;
import com.naesan.evidence.domain.PurchaseEvidenceState;
import com.naesan.passport.application.port.out.AnchorSaltGenerator;
import com.naesan.passport.application.port.out.OutboxEventRepository;
import com.naesan.passport.application.port.out.OwnershipHistoryRepository;
import com.naesan.passport.application.port.out.PassportRepository;
import com.naesan.passport.application.port.out.ProofAnchorRepository;
import com.naesan.passport.domain.AnchorCommitmentCalculator;
import com.naesan.passport.domain.OutboxEvent;
import com.naesan.passport.domain.PassportStatus;
import com.naesan.passport.domain.ProofAnchorState;

class IssuePassportServiceTest {
    private static final UUID OWNER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID OTHER_ACCOUNT_ID = UUID.randomUUID();
    private static final UUID EVIDENCE_ID = UUID.randomUUID();
    private static final UUID SNAPSHOT_ID = UUID.randomUUID();
    private static final Instant ISSUED_AT = Instant.parse("2026-07-18T00:00:00Z");

    private final EvidenceSnapshotRepository snapshotRepository =
            mock(EvidenceSnapshotRepository.class);
    private final PurchaseEvidenceRepository evidenceRepository =
            mock(PurchaseEvidenceRepository.class);
    private final PassportRepository passportRepository = mock(PassportRepository.class);
    private final OwnershipHistoryRepository ownershipHistoryRepository =
            mock(OwnershipHistoryRepository.class);
    private final ProofAnchorRepository proofAnchorRepository =
            mock(ProofAnchorRepository.class);
    private final OutboxEventRepository outboxEventRepository =
            mock(OutboxEventRepository.class);
    private final AnchorSaltGenerator anchorSaltGenerator =
            mock(AnchorSaltGenerator.class);
    private final IssuePassportService service = new IssuePassportService(
            snapshotRepository,
            evidenceRepository,
            passportRepository,
            ownershipHistoryRepository,
            proofAnchorRepository,
            outboxEventRepository,
            anchorSaltGenerator,
            new AnchorCommitmentCalculator(),
            Clock.fixed(ISSUED_AT, ZoneOffset.UTC)
    );

    @BeforeEach
    void prepareConfirmedSnapshot() {
        when(snapshotRepository.findById(SNAPSHOT_ID))
                .thenReturn(Optional.of(snapshot()));
        when(evidenceRepository.findById(EVIDENCE_ID))
                .thenReturn(Optional.of(confirmedEvidence()));
        when(passportRepository.findBySnapshotId(SNAPSHOT_ID))
                .thenReturn(Optional.empty());
        when(anchorSaltGenerator.generate()).thenReturn(new byte[32]);
    }

    @Test
    @DisplayName("확정 snapshot에서 Passport와 proof를 분리된 초기 상태로 발급한다")
    void issuesPassportWithPreparedProof() {
        IssuedPassport issuedPassport = service.issue(OWNER_ACCOUNT_ID, SNAPSHOT_ID);

        assertThat(issuedPassport.passport().status()).isEqualTo(PassportStatus.ACTIVE);
        assertThat(issuedPassport.proofAnchor().state())
                .isEqualTo(ProofAnchorState.PREPARED);
        assertThat(issuedPassport.proofAnchor().passportId())
                .isEqualTo(issuedPassport.passport().id());
    }

    @Test
    @DisplayName("Outbox payload에는 commitment만 넣고 private 발급 재료를 제외한다")
    void excludesPrivateMaterialFromOutboxPayload() {
        ArgumentCaptor<OutboxEvent> outboxEventCaptor =
                ArgumentCaptor.forClass(OutboxEvent.class);

        service.issue(OWNER_ACCOUNT_ID, SNAPSHOT_ID);

        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        String payload = outboxEventCaptor.getValue().payload();
        assertThat(payload)
                .contains("\"schemaVersion\":1", "\"commitment\":")
                .doesNotContain("snapshotDigest", "anchorSalt", OWNER_ACCOUNT_ID.toString());
    }

    @Test
    @DisplayName("다른 계정의 snapshot은 존재 여부를 숨기고 발급하지 않는다")
    void rejectsAnotherOwnersSnapshot() {
        assertThatThrownBy(() -> service.issue(OTHER_ACCOUNT_ID, SNAPSHOT_ID))
                .isInstanceOf(PassportException.class)
                .extracting(exception -> ((PassportException) exception).code())
                .isEqualTo(PassportErrorCode.PASSPORT_SOURCE_NOT_FOUND);

        verify(passportRepository, never()).save(any());
    }

    private EvidenceSnapshot snapshot() {
        return new EvidenceSnapshot(
                SNAPSHOT_ID,
                EVIDENCE_ID,
                1,
                "{}".getBytes(StandardCharsets.UTF_8),
                "a".repeat(64),
                ISSUED_AT
        );
    }

    private PurchaseEvidence confirmedEvidence() {
        return PurchaseEvidence.restore(
                EVIDENCE_ID,
                OWNER_ACCOUNT_ID,
                new EvidenceMetadata(
                        "생각상점",
                        "생각등대",
                        null,
                        java.time.LocalDate.parse("2026-07-01"),
                        new java.math.BigDecimal("1000.00"),
                        "KRW"
                ),
                PurchaseEvidenceState.CONFIRMED,
                1,
                ISSUED_AT,
                ISSUED_AT,
                ISSUED_AT
        );
    }
}
