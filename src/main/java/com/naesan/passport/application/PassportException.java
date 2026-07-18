package com.naesan.passport.application;

public final class PassportException extends RuntimeException {
    private final PassportErrorCode code;

    public PassportException(PassportErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public static PassportException sourceNotFound() {
        return new PassportException(
                PassportErrorCode.PASSPORT_SOURCE_NOT_FOUND,
                "Passport를 발급할 확정 구매 증빙을 찾을 수 없습니다."
        );
    }

    public static PassportException alreadyIssued() {
        return new PassportException(
                PassportErrorCode.PASSPORT_ALREADY_ISSUED,
                "이 구매 증빙에는 Passport가 이미 발급되었습니다."
        );
    }

    public static PassportException notFound() {
        return new PassportException(
                PassportErrorCode.PASSPORT_NOT_FOUND,
                "Passport를 찾을 수 없습니다."
        );
    }

    public PassportErrorCode code() {
        return code;
    }
}
