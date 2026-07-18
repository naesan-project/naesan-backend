package com.naesan.passport.domain;

public enum OutboxEventStatus {
    PENDING,
    CLAIMED,
    SUCCEEDED,
    RETRY_WAIT,
    RECONCILE_PENDING,
    MANUAL_REVIEW,
    DEAD_LETTER
}
