package com.naesan.passport.application.port.out;

import java.time.Instant;

import com.naesan.passport.domain.EvmAnchorEvidence;

public record ProofAnchorReceipt(
        String externalReference,
        Instant anchoredAt,
        boolean confirmed,
        EvmAnchorEvidence evidence
) {

    public ProofAnchorReceipt {
        if (externalReference == null || externalReference.isBlank() || anchoredAt == null) {
            throw new IllegalArgumentException("외부 증명 결과의 필수 값은 null일 수 없습니다.");
        }
    }

    public ProofAnchorReceipt(String externalReference, Instant anchoredAt) {
        this(externalReference, anchoredAt, true, null);
    }

    public ProofAnchorReceipt(
            String externalReference,
            Instant anchoredAt,
            boolean confirmed
    ) {
        this(externalReference, anchoredAt, confirmed, null);
    }
}
