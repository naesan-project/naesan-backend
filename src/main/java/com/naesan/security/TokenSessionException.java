package com.naesan.security;

public final class TokenSessionException extends RuntimeException {
    private final TokenSessionErrorCode code;

    public TokenSessionException(TokenSessionErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public static TokenSessionException invalidRefreshToken() {
        return new TokenSessionException(
                TokenSessionErrorCode.INVALID_REFRESH_TOKEN,
                "다시 로그인해 주세요."
        );
    }

    public TokenSessionErrorCode code() {
        return code;
    }
}
