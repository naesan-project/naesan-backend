package com.naesan.evidence.adapter.in.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateEvidenceMetadataRequest(
        @NotBlank(message = "구매처를 입력해 주세요.")
        @Size(max = 200, message = "구매처는 200자 이하여야 합니다.")
        String merchantName,
        @NotBlank(message = "제품명을 입력해 주세요.")
        @Size(max = 200, message = "제품명은 200자 이하여야 합니다.")
        String productName,
        @Size(max = 200, message = "시리얼 번호는 200자 이하여야 합니다.")
        String serialNumber,
        @NotNull(message = "구매일을 입력해 주세요.")
        LocalDate purchasedAt,
        @NotNull(message = "구매 금액을 입력해 주세요.")
        @DecimalMin(value = "0.00", message = "구매 금액은 0 이상이어야 합니다.")
        @Digits(integer = 17, fraction = 2, message = "구매 금액 형식을 확인해 주세요.")
        BigDecimal amount,
        @NotBlank(message = "통화를 입력해 주세요.")
        String currency
) {
}
