package com.naesan.passport.application.port.out;

public final class ProofProviderException extends RuntimeException {
    private final ProofFailureType failureType;
    private final String errorCode;

    public ProofProviderException(
            ProofFailureType failureType,
            String errorCode
    ) {
        super("외부 증명 provider 요청을 완료하지 못했습니다.");
        if (failureType == null) {
            throw new IllegalArgumentException("Proof failure type은 필수입니다.");
        }
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("Proof error code는 비어 있을 수 없습니다.");
        }
        this.failureType = failureType;
        this.errorCode = errorCode;
    }

    public ProofFailureType failureType() {
        return failureType;
    }

    public String errorCode() {
        return errorCode;
    }
}
