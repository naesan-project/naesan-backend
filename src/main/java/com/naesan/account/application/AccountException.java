package com.naesan.account.application;

public final class AccountException extends RuntimeException {
    private final AccountErrorCode code;

    public AccountException(AccountErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AccountErrorCode code() {
        return code;
    }
}
