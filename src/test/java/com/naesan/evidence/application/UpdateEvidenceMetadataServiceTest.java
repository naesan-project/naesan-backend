package com.naesan.evidence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.evidence.domain.EvidenceMetadata;
import com.naesan.evidence.domain.PurchaseEvidence;

class UpdateEvidenceMetadataServiceTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("0ab1785a-baf0-4d35-870a-a53f97d2fec7");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("7b9670b3-5964-4a27-ad96-3b2345b90e87");
    private static final Instant CREATED_AT = Instant.parse("2026-07-17T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("owner가 확정 전 구매 정보를 수정한다")
    void updatesMetadata() {
        FakePurchaseEvidenceRepository repository =
                new FakePurchaseEvidenceRepository(draft());
        UpdateEvidenceMetadataService service =
                new UpdateEvidenceMetadataService(repository, CLOCK);

        PurchaseEvidence updated = service.update(command(OWNER_ACCOUNT_ID));

        assertThat(updated.metadata().merchantName()).isEqualTo("새 상점");
        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.updatedAt()).isEqualTo(NOW);
        assertThat(repository.evidence).isEqualTo(updated);
    }

    @Test
    @DisplayName("다른 계정의 Evidence 수정 요청은 존재하지 않는 것처럼 거절한다")
    void hidesEvidenceFromOtherAccount() {
        FakePurchaseEvidenceRepository repository =
                new FakePurchaseEvidenceRepository(draft());
        UpdateEvidenceMetadataService service =
                new UpdateEvidenceMetadataService(repository, CLOCK);

        assertThatThrownBy(() -> service.update(command(UUID.randomUUID())))
                .isInstanceOf(EvidenceException.class)
                .extracting(exception -> ((EvidenceException) exception).code())
                .isEqualTo(EvidenceErrorCode.EVIDENCE_NOT_FOUND);
    }

    private UpdateEvidenceMetadataCommand command(UUID ownerAccountId) {
        return new UpdateEvidenceMetadataCommand(
                ownerAccountId,
                EVIDENCE_ID,
                "새 상점",
                "새 제품",
                null,
                LocalDate.parse("2026-07-01"),
                new BigDecimal("2000.00"),
                "KRW"
        );
    }

    private PurchaseEvidence draft() {
        return PurchaseEvidence.createDraft(
                EVIDENCE_ID,
                OWNER_ACCOUNT_ID,
                new EvidenceMetadata(
                        "생각상점",
                        "생각등대",
                        null,
                        LocalDate.parse("2026-07-01"),
                        new BigDecimal("1000.00"),
                        "KRW"
                ),
                CREATED_AT
        );
    }

    private static final class FakePurchaseEvidenceRepository
            implements PurchaseEvidenceRepository {
        private PurchaseEvidence evidence;

        private FakePurchaseEvidenceRepository(PurchaseEvidence evidence) {
            this.evidence = evidence;
        }

        @Override
        public void save(PurchaseEvidence evidence) {
            this.evidence = evidence;
        }

        @Override
        public void update(PurchaseEvidence evidence) {
            this.evidence = evidence;
        }

        @Override
        public List<PurchaseEvidence> findAllByOwnerAccountId(UUID ownerAccountId) {
            return Optional.ofNullable(evidence)
                    .filter(foundEvidence ->
                            foundEvidence.ownerAccountId().equals(ownerAccountId))
                    .stream()
                    .toList();
        }

        @Override
        public Optional<PurchaseEvidence> findById(UUID evidenceId) {
            return Optional.ofNullable(evidence)
                    .filter(foundEvidence -> foundEvidence.id().equals(evidenceId));
        }
    }
}
