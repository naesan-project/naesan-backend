package com.naesan.passport.application;

public final class OutboxOperationException extends RuntimeException {
    private final OutboxOperationErrorCode code;

    private OutboxOperationException(
            OutboxOperationErrorCode code,
            String message
    ) {
        super(message);
        this.code = code;
    }

    public static OutboxOperationException eventNotReprocessable() {
        return new OutboxOperationException(
                OutboxOperationErrorCode.EVENT_NOT_REPROCESSABLE,
                "재처리할 수 있는 Outbox event를 찾을 수 없습니다."
        );
    }

    public static OutboxOperationException reprocessConflict() {
        return new OutboxOperationException(
                OutboxOperationErrorCode.REPROCESS_CONFLICT,
                "Outbox event 재처리 상태가 이미 변경되었습니다."
        );
    }

    public OutboxOperationErrorCode code() {
        return code;
    }
}
