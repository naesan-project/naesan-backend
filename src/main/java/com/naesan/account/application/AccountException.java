package com.naesan.account.application;

public final class AccountException extends RuntimeException {
    private final AccountErrorCode code;

    public AccountException(AccountErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public static AccountException emailAlreadyRegistered() {
        return new AccountException(
                AccountErrorCode.EMAIL_ALREADY_REGISTERED,
                "이미 등록된 이메일입니다."
        );
    }

    public static AccountException invalidCredentials() {
        return new AccountException(
                AccountErrorCode.INVALID_CREDENTIALS,
                "이메일 또는 비밀번호가 올바르지 않습니다."
        );
    }

    public AccountErrorCode code() {
        return code;
    }
}
