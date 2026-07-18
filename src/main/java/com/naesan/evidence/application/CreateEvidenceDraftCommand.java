package com.naesan.evidence.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateEvidenceDraftCommand(
        UUID ownerAccountId,
        String merchantName,
        String productName,
        String serialNumber,
        LocalDate purchasedAt,
        BigDecimal amount,
        String currency
) {
}
