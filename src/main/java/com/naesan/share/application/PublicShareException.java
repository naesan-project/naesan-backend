package com.naesan.share.application;

public final class PublicShareException extends RuntimeException {
    private final PublicShareErrorCode code;

    private PublicShareException(PublicShareErrorCode code, String message) {
        super(message);
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

    public PublicShareErrorCode code() {
        return code;
    }
}
