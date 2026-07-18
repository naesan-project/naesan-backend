package com.naesan.evidence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PurchaseEvidenceTest {
    private static final UUID EVIDENCE_ID =
            UUID.fromString("bc75fb7a-7938-4711-8b79-17b8a48c9674");
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("298b32e9-c44a-4b62-bf4a-12b1ea9b5190");
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");

    @Test
    @DisplayName("구매 증빙 draft를 생성한다")
    void createsDraft() {
        PurchaseEvidence evidence = PurchaseEvidence.createDraft(
                EVIDENCE_ID,
                OWNER_ACCOUNT_ID,
                metadata(),
                CREATED_AT
        );

        assertThat(evidence.id()).isEqualTo(EVIDENCE_ID);
        assertThat(evidence.ownerAccountId()).isEqualTo(OWNER_ACCOUNT_ID);
        assertThat(evidence.state()).isEqualTo(PurchaseEvidenceState.DRAFT);
        assertThat(evidence.version()).isZero();
        assertThat(evidence.createdAt()).isEqualTo(CREATED_AT);
        assertThat(evidence.updatedAt()).isEqualTo(CREATED_AT);
        assertThat(evidence.confirmedAt()).isNull();
    }

    @Test
    @DisplayName("음수 version을 가진 구매 증빙을 거절한다")
    void rejectsNegativeVersion() {
        assertThatThrownBy(() -> PurchaseEvidence.restore(
                EVIDENCE_ID,
                OWNER_ACCOUNT_ID,
                metadata(),
                PurchaseEvidenceState.DRAFT,
                -1,
                CREATED_AT,
                CREATED_AT,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("구매 증빙 version은 0 이상이어야 합니다.");
    }

    @Test
    @DisplayName("생성 시각보다 빠른 수정 시각을 거절한다")
    void rejectsUpdatedAtBeforeCreatedAt() {
        assertThatThrownBy(() -> PurchaseEvidence.restore(
                EVIDENCE_ID,
                OWNER_ACCOUNT_ID,
                metadata(),
                PurchaseEvidenceState.DRAFT,
                0,
                CREATED_AT,
                CREATED_AT.minusSeconds(1),
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("수정 시각은 생성 시각보다 빠를 수 없습니다.");
    }

    @Test
    @DisplayName("확정 상태와 확정 시각이 일치하지 않으면 거절한다")
    void rejectsInconsistentConfirmation() {
        assertThatThrownBy(() -> PurchaseEvidence.restore(
                EVIDENCE_ID,
                OWNER_ACCOUNT_ID,
                metadata(),
                PurchaseEvidenceState.CONFIRMED,
                1,
                CREATED_AT,
                CREATED_AT,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("확정 상태와 확정 시각이 일치해야 합니다.");
    }

    private EvidenceMetadata metadata() {
        return new EvidenceMetadata(
                "생각상점",
                "생각등대",
                null,
                LocalDate.parse("2026-07-01"),
                new BigDecimal("1000.00"),
                "KRW"
        );
    }
}
