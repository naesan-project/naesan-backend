package com.naesan.passport.application.port.out;

import java.time.Instant;

public record ProofAnchorReceipt(
        String externalReference,
        Instant anchoredAt,
        boolean confirmed
) {

    public ProofAnchorReceipt {
        if (externalReference == null || externalReference.isBlank() || anchoredAt == null) {
            throw new IllegalArgumentException("외부 증명 결과의 필수 값은 null일 수 없습니다.");
        }
    }

    public ProofAnchorReceipt(String externalReference, Instant anchoredAt) {
        this(externalReference, anchoredAt, true);
    }
}
