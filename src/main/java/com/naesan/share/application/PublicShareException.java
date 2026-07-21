package com.naesan.share.application;

public final class PublicShareException extends RuntimeException {
    private final PublicShareErrorCode code;

    private PublicShareException(PublicShareErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    private PublicShareException(
            PublicShareErrorCode code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
    }

    public static PublicShareException notFound() {
        return new PublicShareException(
                PublicShareErrorCode.PUBLIC_SHARE_NOT_FOUND,
                "Public share를 찾을 수 없습니다."
        );
    }

    public static PublicShareException alreadyActive() {
        return new PublicShareException(
                PublicShareErrorCode.PUBLIC_SHARE_ALREADY_ACTIVE,
                "사용 중인 Public share가 이미 있습니다."
        );
    }

    public static PublicShareException emptyFile() {
        return new PublicShareException(
                PublicShareErrorCode.PUBLIC_FILE_EMPTY,
                "대조할 파일이 비어 있습니다."
        );
    }

    public static PublicShareException fileTooLarge() {
        return new PublicShareException(
                PublicShareErrorCode.PUBLIC_FILE_TOO_LARGE,
                "대조할 파일의 크기 제한을 초과했습니다."
        );
    }

    public static PublicShareException unsupportedFile() {
        return new PublicShareException(
                PublicShareErrorCode.PUBLIC_FILE_UNSUPPORTED,
                "JPEG, PNG, PDF 파일만 대조할 수 있습니다."
        );
    }

    public static PublicShareException fileTypeMismatch() {
        return new PublicShareException(
                PublicShareErrorCode.PUBLIC_FILE_TYPE_MISMATCH,
                "파일 내용이 선언된 형식과 일치하지 않습니다."
        );
    }

    public static PublicShareException fileReadFailed(Throwable cause) {
        return new PublicShareException(
                PublicShareErrorCode.PUBLIC_FILE_READ_FAILED,
                "대조할 파일을 읽지 못했습니다.",
                cause
        );
    }

    public PublicShareErrorCode code() {
        return code;
    }
}
