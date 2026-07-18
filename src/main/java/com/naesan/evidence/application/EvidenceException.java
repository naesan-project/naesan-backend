package com.naesan.evidence.application;

public final class EvidenceException extends RuntimeException {
    private final EvidenceErrorCode code;

    public EvidenceException(EvidenceErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public static EvidenceException notFound() {
        return new EvidenceException(
                EvidenceErrorCode.EVIDENCE_NOT_FOUND,
                "구매 증빙을 찾을 수 없습니다."
        );
    }

    public static EvidenceException notEditable() {
        return new EvidenceException(
                EvidenceErrorCode.EVIDENCE_NOT_EDITABLE,
                "현재 상태에서는 구매 증빙을 수정할 수 없습니다."
        );
    }

    public static EvidenceException fileAlreadyAttached() {
        return new EvidenceException(
                EvidenceErrorCode.FILE_ALREADY_ATTACHED,
                "구매 증빙에 파일이 이미 연결되어 있습니다."
        );
    }

    public static EvidenceException fileUnavailable() {
        return new EvidenceException(
                EvidenceErrorCode.FILE_UNAVAILABLE,
                "구매 증빙 파일을 사용할 수 없습니다."
        );
    }

    public static EvidenceException concurrentModification() {
        return new EvidenceException(
                EvidenceErrorCode.CONCURRENT_MODIFICATION,
                "구매 증빙이 다른 요청에서 변경되었습니다."
        );
    }

    public EvidenceErrorCode code() {
        return code;
    }
}
