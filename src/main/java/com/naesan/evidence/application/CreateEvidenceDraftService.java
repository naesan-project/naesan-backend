package com.naesan.evidence.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.evidence.domain.EvidenceMetadata;
import com.naesan.evidence.domain.PurchaseEvidence;

public class CreateEvidenceDraftService {
    private final PurchaseEvidenceRepository evidenceRepository;
    private final Clock clock;

    public CreateEvidenceDraftService(
            PurchaseEvidenceRepository evidenceRepository,
            Clock clock
    ) {
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public PurchaseEvidence create(CreateEvidenceDraftCommand command) {
        Objects.requireNonNull(command);

        EvidenceMetadata metadata = new EvidenceMetadata(
                command.merchantName(),
                command.productName(),
                command.serialNumber(),
                command.purchasedAt(),
                command.amount(),
                command.currency()
        );
        requireNotFuturePurchase(metadata.purchasedAt());

        PurchaseEvidence evidence = PurchaseEvidence.createDraft(
                UUID.randomUUID(),
                command.ownerAccountId(),
                metadata,
                clock.instant()
        );
        evidenceRepository.save(evidence);
        return evidence;
    }

    private void requireNotFuturePurchase(LocalDate purchasedAt) {
        if (purchasedAt.isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("구매일은 미래일 수 없습니다.");
        }
    }
}
