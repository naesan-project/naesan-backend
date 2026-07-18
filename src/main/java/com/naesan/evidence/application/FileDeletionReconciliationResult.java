package com.naesan.evidence.application;

public record FileDeletionReconciliationResult(
        int attempted,
        int deleted,
        int failed
) {
}
