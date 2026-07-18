package com.naesan.evidence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvidenceMetadataTest {
    private static final LocalDate PURCHASED_AT = LocalDate.parse("2026-07-01");

    @Test
    @DisplayName("구매 정보를 정규화한다")
    void normalizesMetadata() {
        EvidenceMetadata metadata = new EvidenceMetadata(
                "  생각상점  ",
                "  생각등대  ",
                "   ",
                PURCHASED_AT,
                new BigDecimal("1000"),
                " krw "
        );

        assertThat(metadata.merchantName()).isEqualTo("생각상점");
        assertThat(metadata.productName()).isEqualTo("생각등대");
        assertThat(metadata.serialNumber()).isNull();
        assertThat(metadata.amount()).isEqualByComparingTo("1000.00");
        assertThat(metadata.currency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("비어 있는 필수 구매 정보를 거절한다")
    void rejectsBlankRequiredText() {
        assertThatThrownBy(() -> metadata(" ", new BigDecimal("1000.00"), "KRW"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("필수 구매 정보가 비어 있습니다: 구매처");
    }

    @Test
    @DisplayName("200자를 넘는 구매 정보를 거절한다")
    void rejectsLongText() {
        assertThatThrownBy(() -> metadata("가".repeat(201), new BigDecimal("1000.00"), "KRW"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("구매 정보는 200자 이하여야 합니다: 구매처");
    }

    @Test
    @DisplayName("음수 금액을 거절한다")
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> metadata("생각상점", new BigDecimal("-0.01"), "KRW"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("구매 금액은 0 이상이어야 합니다.");
    }

    @Test
    @DisplayName("반올림이 필요한 금액을 거절한다")
    void rejectsAmountRequiringRounding() {
        assertThatThrownBy(() -> metadata("생각상점", new BigDecimal("1000.001"), "KRW"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("구매 금액은 소수 2자리까지만 입력할 수 있습니다.");
    }

    @Test
    @DisplayName("ASCII 세 글자가 아닌 통화를 거절한다")
    void rejectsInvalidCurrency() {
        assertThatThrownBy(() -> metadata("생각상점", new BigDecimal("1000.00"), "원화"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("통화는 대문자 ASCII 세 글자여야 합니다.");
    }

    private EvidenceMetadata metadata(
            String merchantName,
            BigDecimal amount,
            String currency
    ) {
        return new EvidenceMetadata(
                merchantName,
                "생각등대",
                null,
                PURCHASED_AT,
                amount,
                currency
        );
    }
}
