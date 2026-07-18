package com.naesan.passport.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.naesan.evidence.application.port.out.EvidenceSnapshotRepository;
import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.evidence.domain.EvidenceSnapshot;
import com.naesan.evidence.domain.PurchaseEvidenceState;
import com.naesan.passport.application.port.out.AnchorSaltGenerator;
import com.naesan.passport.application.port.out.OutboxEventRepository;
import com.naesan.passport.application.port.out.OwnershipHistoryRepository;
import com.naesan.passport.application.port.out.PassportRepository;
import com.naesan.passport.application.port.out.ProofAnchorRepository;
import com.naesan.passport.domain.AnchorCommitment;
import com.naesan.passport.domain.AnchorCommitmentCalculator;
import com.naesan.passport.domain.OutboxEvent;
import com.naesan.passport.domain.OwnershipHistory;
import com.naesan.passport.domain.Passport;
import com.naesan.passport.domain.ProofAnchor;

public class IssuePassportService {
    private static final int OUTBOX_SCHEMA_VERSION = 1;
    private static final String DISPATCH_KEY_PREFIX = "proof-anchor:";

    private final EvidenceSnapshotRepository snapshotRepository;
    private final PurchaseEvidenceRepository evidenceRepository;
    private final PassportRepository passportRepository;
    private final OwnershipHistoryRepository ownershipHistoryRepository;
    private final ProofAnchorRepository proofAnchorRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AnchorSaltGenerator anchorSaltGenerator;
    private final AnchorCommitmentCalculator commitmentCalculator;
    private final Clock clock;

    public IssuePassportService(
            EvidenceSnapshotRepository snapshotRepository,
            PurchaseEvidenceRepository evidenceRepository,
            PassportRepository passportRepository,
            OwnershipHistoryRepository ownershipHistoryRepository,
            ProofAnchorRepository proofAnchorRepository,
            OutboxEventRepository outboxEventRepository,
            AnchorSaltGenerator anchorSaltGenerator,
            AnchorCommitmentCalculator commitmentCalculator,
            Clock clock
    ) {
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository);
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository);
        this.passportRepository = Objects.requireNonNull(passportRepository);
        this.ownershipHistoryRepository = Objects.requireNonNull(ownershipHistoryRepository);
        this.proofAnchorRepository = Objects.requireNonNull(proofAnchorRepository);
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository);
        this.anchorSaltGenerator = Objects.requireNonNull(anchorSaltGenerator);
        this.commitmentCalculator = Objects.requireNonNull(commitmentCalculator);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public IssuedPassport issue(UUID ownerAccountId, UUID snapshotId) {
        EvidenceSnapshot snapshot = ownedConfirmedSnapshot(ownerAccountId, snapshotId);
        if (passportRepository.findBySnapshotId(snapshotId).isPresent()) {
            throw PassportException.alreadyIssued();
        }

        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        UUID passportId = UUID.randomUUID();
        UUID proofAnchorId = UUID.randomUUID();
        AnchorCommitment commitment = commitmentCalculator.calculate(
                snapshot.snapshotDigest(),
                anchorSaltGenerator.generate()
        );
        Passport passport = Passport.issue(
                passportId,
                snapshotId,
                ownerAccountId,
                issuedAt
        );
        OwnershipHistory ownershipHistory = OwnershipHistory.recordIssuance(
                UUID.randomUUID(),
                passportId,
                ownerAccountId,
                issuedAt
        );
        ProofAnchor proofAnchor = ProofAnchor.prepare(
                proofAnchorId,
                passportId,
                commitment,
                issuedAt
        );
        OutboxEvent outboxEvent = OutboxEvent.createProofAnchorRequest(
                UUID.randomUUID(),
                passportId,
                proofAnchorId,
                OUTBOX_SCHEMA_VERSION,
                proofAnchorPayload(commitment),
                DISPATCH_KEY_PREFIX + proofAnchorId,
                issuedAt
        );

        passportRepository.save(passport);
        ownershipHistoryRepository.append(ownershipHistory);
        proofAnchorRepository.save(proofAnchor);
        outboxEventRepository.save(outboxEvent);
        return new IssuedPassport(passport, proofAnchor);
    }

    private EvidenceSnapshot ownedConfirmedSnapshot(
            UUID ownerAccountId,
            UUID snapshotId
    ) {
        EvidenceSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(PassportException::sourceNotFound);
        boolean ownedConfirmedEvidence = evidenceRepository.findById(snapshot.evidenceId())
                .filter(evidence -> evidence.ownerAccountId().equals(ownerAccountId))
                .filter(evidence -> evidence.state() == PurchaseEvidenceState.CONFIRMED)
                .isPresent();
        if (!ownedConfirmedEvidence) {
            throw PassportException.sourceNotFound();
        }
        return snapshot;
    }

    private String proofAnchorPayload(AnchorCommitment commitment) {
        return """
                {"schemaVersion":%d,"commitment":"%s"}\
                """.formatted(
                commitment.schemaVersion(),
                HexFormat.of().formatHex(commitment.commitment())
        );
    }
}
