package com.naesan.passport.application;

public enum OutboxOperationErrorCode {
    EVENT_NOT_REPROCESSABLE,
    REPROCESS_CONFLICT
}
