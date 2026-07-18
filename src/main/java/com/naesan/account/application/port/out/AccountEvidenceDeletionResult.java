package com.naesan.account.application.port.out;

public record AccountEvidenceDeletionResult(
        int evidenceCount,
        int deletedFileCount,
        int pendingFileCount
) {
}
