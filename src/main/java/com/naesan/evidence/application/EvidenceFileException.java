package com.naesan.evidence.application;

public final class EvidenceFileException extends RuntimeException {
    private final EvidenceFileErrorCode code;

    public EvidenceFileException(EvidenceFileErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public EvidenceFileException(
            EvidenceFileErrorCode code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
    }

    public EvidenceFileErrorCode code() {
        return code;
    }
}
