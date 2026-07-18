package com.naesan.passport.application;

public final class OutboxProcessingException extends RuntimeException {

    public OutboxProcessingException(String message) {
        super(message);
    }
}
