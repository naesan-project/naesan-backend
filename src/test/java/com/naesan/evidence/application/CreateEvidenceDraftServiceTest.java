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
import com.naesan.evidence.domain.PurchaseEvidence;
import com.naesan.evidence.domain.PurchaseEvidenceState;

class CreateEvidenceDraftServiceTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("290a2ec0-e9cc-4c29-9463-55baf8985a45");
    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("owner의 구매 정보로 Evidence draft를 저장한다")
    void createsDraft() {
        FakePurchaseEvidenceRepository repository = new FakePurchaseEvidenceRepository();
        CreateEvidenceDraftService service = new CreateEvidenceDraftService(repository, CLOCK);

        PurchaseEvidence evidence = service.create(command(LocalDate.parse("2026-07-01")));

        assertThat(evidence.ownerAccountId()).isEqualTo(OWNER_ACCOUNT_ID);
        assertThat(evidence.state()).isEqualTo(PurchaseEvidenceState.DRAFT);
        assertThat(evidence.createdAt()).isEqualTo(NOW);
        assertThat(repository.savedEvidence).isEqualTo(evidence);
    }

    @Test
    @DisplayName("미래 구매일은 저장하지 않는다")
    void rejectsFuturePurchaseDate() {
        FakePurchaseEvidenceRepository repository = new FakePurchaseEvidenceRepository();
        CreateEvidenceDraftService service = new CreateEvidenceDraftService(repository, CLOCK);

        assertThatThrownBy(() -> service.create(command(LocalDate.parse("2026-07-19"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("구매일은 미래일 수 없습니다.");
        assertThat(repository.savedEvidence).isNull();
    }

    private CreateEvidenceDraftCommand command(LocalDate purchasedAt) {
        return new CreateEvidenceDraftCommand(
                OWNER_ACCOUNT_ID,
                "생각상점",
                "생각등대",
                null,
                purchasedAt,
                new BigDecimal("1000.00"),
                "KRW"
        );
    }

    private static final class FakePurchaseEvidenceRepository
            implements PurchaseEvidenceRepository {
        private PurchaseEvidence savedEvidence;

        @Override
        public void save(PurchaseEvidence evidence) {
            this.savedEvidence = evidence;
        }

        @Override
        public void update(PurchaseEvidence evidence) {
            this.savedEvidence = evidence;
        }

        @Override
        public List<PurchaseEvidence> findAllByOwnerAccountId(UUID ownerAccountId) {
            return Optional.ofNullable(savedEvidence)
                    .filter(evidence -> evidence.ownerAccountId().equals(ownerAccountId))
                    .stream()
                    .toList();
        }

        @Override
        public Optional<PurchaseEvidence> findById(UUID evidenceId) {
            return Optional.ofNullable(savedEvidence)
                    .filter(evidence -> evidence.id().equals(evidenceId));
        }
    }
}
