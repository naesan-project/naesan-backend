package com.naesan.account.domain;

import java.util.regex.Pattern;

public final class PasswordHash {
    private static final Pattern BCRYPT_PATTERN =
            Pattern.compile("^\\$2[ayb]\\$12\\$[./A-Za-z0-9]{53}$");

    private final String value;

    public PasswordHash(String value) {
        validate(value);
        this.value = value;
    }

    private static void validate(String value) {
        if (value == null || !BCRYPT_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("비밀번호 해시는 BCrypt cost 12 형식이어야 합니다.");
        }
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof PasswordHash that)) {
            return false;
        }

        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "PasswordHash[REDACTED]";
    }
}
