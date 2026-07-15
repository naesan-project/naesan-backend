package com.naesan.account.domain;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

public record Email(String value) {
    private static final int MAX_BYTE_LENGTH = 254;
    private static final int FIRST_VISIBLE_ASCII = '!';
    private static final int LAST_VISIBLE_ASCII = '~';
    private static final char EMAIL_SEPARATOR = '@';

    public Email {
        value = normalize(value);
        validate(value);
    }

    private static String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("이메일은 null일 수 없습니다.");
        }

        return Normalizer.normalize(value.strip(), Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT);
    }

    private static void validate(String value) {
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTE_LENGTH) {
            throw new IllegalArgumentException("이메일은 " + MAX_BYTE_LENGTH + " byte 이하여야 합니다.");
        }

        if (!isVisibleAscii(value)) {
            throw new IllegalArgumentException("이메일은 공백과 제어 문자를 제외한 ASCII 문자만 사용할 수 있습니다.");
        }

        int separatorIndex = value.indexOf(EMAIL_SEPARATOR);
        if (separatorIndex <= 0
                || separatorIndex != value.lastIndexOf(EMAIL_SEPARATOR)
                || separatorIndex == value.length() - 1) {
            throw new IllegalArgumentException("이메일은 @ 앞뒤에 값이 있어야 하며 @는 하나만 사용할 수 있습니다.");
        }
    }

    private static boolean isVisibleAscii(String value) {
        return value.chars()
                .allMatch(character -> character >= FIRST_VISIBLE_ASCII
                        && character <= LAST_VISIBLE_ASCII);
    }
}
