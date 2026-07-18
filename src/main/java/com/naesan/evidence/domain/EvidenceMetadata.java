package com.naesan.evidence.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;

public record EvidenceMetadata(
        String merchantName,
        String productName,
        String serialNumber,
        LocalDate purchasedAt,
        BigDecimal amount,
        String currency
) {
    private static final int MAX_TEXT_LENGTH = 200;
    private static final int CURRENCY_LENGTH = 3;
    private static final int AMOUNT_SCALE = 2;

    public EvidenceMetadata {
        merchantName = normalizeRequiredText(merchantName, "구매처");
        productName = normalizeRequiredText(productName, "제품명");
        serialNumber = normalizeOptionalText(serialNumber, "시리얼 번호");
        if (purchasedAt == null) {
            throw new IllegalArgumentException("구매일은 null일 수 없습니다.");
        }
        amount = normalizeAmount(amount);
        currency = normalizeCurrency(currency);
    }

    private static String normalizeRequiredText(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "은 null일 수 없습니다.");
        }

        String normalizedValue = normalizeText(value);
        if (normalizedValue.isBlank()) {
            throw new IllegalArgumentException("필수 구매 정보가 비어 있습니다: " + fieldName);
        }
        requireMaximumLength(normalizedValue, fieldName);
        return normalizedValue;
    }

    private static String normalizeOptionalText(String value, String fieldName) {
        if (value == null) {
            return null;
        }

        String normalizedValue = normalizeText(value);
        if (normalizedValue.isBlank()) {
            return null;
        }
        requireMaximumLength(normalizedValue, fieldName);
        return normalizedValue;
    }

    private static String normalizeText(String value) {
        return Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
    }

    private static void requireMaximumLength(String value, String fieldName) {
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "구매 정보는 " + MAX_TEXT_LENGTH + "자 이하여야 합니다: " + fieldName
            );
        }
    }

    private static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("구매 금액은 null일 수 없습니다.");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("구매 금액은 0 이상이어야 합니다.");
        }
        if (amount.scale() > AMOUNT_SCALE) {
            throw new IllegalArgumentException(
                    "구매 금액은 소수 " + AMOUNT_SCALE + "자리까지만 입력할 수 있습니다."
            );
        }
        return amount.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY);
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null) {
            throw new IllegalArgumentException("통화는 null일 수 없습니다.");
        }

        String normalizedCurrency = currency.strip().toUpperCase(Locale.ROOT);
        if (normalizedCurrency.length() != CURRENCY_LENGTH
                || !normalizedCurrency.chars().allMatch(EvidenceMetadata::isUppercaseAscii)) {
            throw new IllegalArgumentException("통화는 대문자 ASCII 세 글자여야 합니다.");
        }
        return normalizedCurrency;
    }

    private static boolean isUppercaseAscii(int character) {
        return character >= 'A' && character <= 'Z';
    }

    public void requirePurchasedOnOrBefore(LocalDate date) {
        if (purchasedAt.isAfter(date)) {
            throw new IllegalArgumentException("구매일은 미래일 수 없습니다.");
        }
    }
}
