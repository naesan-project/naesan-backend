package com.naesan.passport.application;

import java.util.UUID;

public record ReprocessOutboxEventCommand(
        UUID eventId,
        String operatorId,
        String reason
) {
    private static final int MAXIMUM_OPERATOR_ID_LENGTH = 100;
    private static final int MAXIMUM_REASON_LENGTH = 500;

    public ReprocessOutboxEventCommand {
        if (eventId == null) {
            throw new IllegalArgumentException("재처리할 Outbox event ID는 필수입니다.");
        }
        if (operatorId == null || operatorId.isBlank()) {
            throw new IllegalArgumentException("재처리 운영자 ID는 비어 있을 수 없습니다.");
        }
        if (operatorId.length() > MAXIMUM_OPERATOR_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "재처리 운영자 ID는 " + MAXIMUM_OPERATOR_ID_LENGTH + "자 이하여야 합니다."
            );
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("재처리 사유는 비어 있을 수 없습니다.");
        }
        if (reason.length() > MAXIMUM_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "재처리 사유는 " + MAXIMUM_REASON_LENGTH + "자 이하여야 합니다."
            );
        }
    }
}
