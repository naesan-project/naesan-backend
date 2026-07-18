package com.naesan.evidence.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.evidence.domain.EvidenceMetadata;
import com.naesan.evidence.domain.PurchaseEvidence;
import com.naesan.evidence.domain.PurchaseEvidenceState;

public class UpdateEvidenceMetadataService {
    private final PurchaseEvidenceRepository evidenceRepository;
    private final Clock clock;

    public UpdateEvidenceMetadataService(
            PurchaseEvidenceRepository evidenceRepository,
            Clock clock
    ) {
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public PurchaseEvidence update(UpdateEvidenceMetadataCommand command) {
        Objects.requireNonNull(command);

        PurchaseEvidence evidence = evidenceRepository.findById(command.evidenceId())
                .filter(foundEvidence -> foundEvidence.ownerAccountId()
                        .equals(command.ownerAccountId()))
                .orElseThrow(EvidenceException::notFound);
        if (evidence.state() == PurchaseEvidenceState.CONFIRMED) {
            throw EvidenceException.notEditable();
        }

        EvidenceMetadata metadata = new EvidenceMetadata(
                command.merchantName(),
                command.productName(),
                command.serialNumber(),
                command.purchasedAt(),
                command.amount(),
                command.currency()
        );
        metadata.requirePurchasedOnOrBefore(LocalDate.now(clock));

        PurchaseEvidence updatedEvidence = evidence.updateMetadata(
                metadata,
                clock.instant()
        );
        evidenceRepository.update(updatedEvidence);
        return updatedEvidence;
    }

}
