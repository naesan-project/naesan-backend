package com.naesan.account.domain;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private static final int MIN_BYTE_LENGTH = 12;
    private static final int MAX_BYTE_LENGTH = 64;

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("비밀번호는 null일 수 없습니다.");
        }

        int byteLength = rawPassword.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength < MIN_BYTE_LENGTH || byteLength > MAX_BYTE_LENGTH) {
            throw new IllegalArgumentException(
                    "비밀번호는 " + MIN_BYTE_LENGTH + "~" + MAX_BYTE_LENGTH + " UTF-8 byte여야 합니다."
            );
        }
    }
}
