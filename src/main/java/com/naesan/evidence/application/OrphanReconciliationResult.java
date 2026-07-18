package com.naesan.evidence.application;

public record OrphanReconciliationResult(
        int scanned,
        int deleted,
        int failed
) {
}
