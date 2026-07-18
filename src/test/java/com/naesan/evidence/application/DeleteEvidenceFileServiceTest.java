package com.naesan.evidence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.naesan.evidence.application.port.out.EvidenceFileRepository;
import com.naesan.evidence.application.port.out.FileStorage;
import com.naesan.evidence.application.port.out.FileStorageException;
import com.naesan.evidence.application.port.out.StoredObjectMetadata;
import com.naesan.evidence.domain.EvidenceFile;
import com.naesan.evidence.domain.EvidenceFileState;
import com.naesan.evidence.domain.EvidenceFileType;
import com.naesan.evidence.domain.StorageKey;

class DeleteEvidenceFileServiceTest {
    private static final UUID EVIDENCE_ID =
            UUID.fromString("70024349-f11f-4c31-ab3e-a7fd9392581e");
    private static final StorageKey OBJECT_KEY = new StorageKey("permanent/file");
    private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("저장소 삭제 실패 시 파일을 삭제 대기 상태로 남긴다")
    void keepsPendingStateWhenStorageDeletionFails() {
        FakeEvidenceFileRepository repository =
                new FakeEvidenceFileRepository(promotedFile());
        FailingDeletionStorage storage = new FailingDeletionStorage();
        DeleteEvidenceFileService service =
                new DeleteEvidenceFileService(repository, storage, CLOCK);

        assertThatThrownBy(() -> service.delete(EVIDENCE_ID))
                .isInstanceOf(FileStorageException.class);
        assertThat(repository.evidenceFile.state())
                .isEqualTo(EvidenceFileState.DELETION_PENDING);
    }

    @Test
    @DisplayName("삭제 대기 파일을 재시도해 삭제 완료한다")
    void retriesPendingDeletion() {
        FakeEvidenceFileRepository repository =
                new FakeEvidenceFileRepository(
                        promotedFile().requestDeletion(NOW.minusSeconds(1))
                );
        SuccessfulDeletionStorage storage = new SuccessfulDeletionStorage();
        DeleteEvidenceFileService service =
                new DeleteEvidenceFileService(repository, storage, CLOCK);

        FileDeletionReconciliationResult result =
                service.reconcilePendingDeletions();

        assertThat(result).isEqualTo(
                new FileDeletionReconciliationResult(1, 1, 0)
        );
        assertThat(repository.evidenceFile.state())
                .isEqualTo(EvidenceFileState.DELETED);
        assertThat(storage.deletedKey).isEqualTo(OBJECT_KEY);
    }

    private EvidenceFile promotedFile() {
        return EvidenceFile.createTemporary(
                UUID.randomUUID(),
                EVIDENCE_ID,
                new StorageKey("temporary/file"),
                "a".repeat(64),
                EvidenceFileType.PDF,
                1024,
                NOW.minusSeconds(2)
        ).promote(OBJECT_KEY, NOW.minusSeconds(1));
    }

    private static final class FakeEvidenceFileRepository
            implements EvidenceFileRepository {
        private EvidenceFile evidenceFile;

        private FakeEvidenceFileRepository(EvidenceFile evidenceFile) {
            this.evidenceFile = evidenceFile;
        }

        @Override
        public void save(EvidenceFile evidenceFile) {
            this.evidenceFile = evidenceFile;
        }

        @Override
        public void update(EvidenceFile evidenceFile) {
            this.evidenceFile = evidenceFile;
        }

        @Override
        public Set<StorageKey> findAllObjectKeys() {
            return Set.of(evidenceFile.objectKey());
        }

        @Override
        public List<EvidenceFile> findAllByState(EvidenceFileState state) {
            if (evidenceFile.state() == state) {
                return List.of(evidenceFile);
            }
            return List.of();
        }

        @Override
        public Optional<EvidenceFile> findByEvidenceId(UUID evidenceId) {
            return Optional.of(evidenceFile)
                    .filter(file -> file.evidenceId().equals(evidenceId));
        }
    }

    private static class SuccessfulDeletionStorage implements FileStorage {
        private StorageKey deletedKey;

        @Override
        public StorageKey storeTemporary(InputStream content) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream open(StorageKey key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StorageKey promote(StorageKey temporaryKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StoredObjectMetadata> listPermanentObjects() {
            return List.of();
        }

        @Override
        public void delete(StorageKey key) {
            this.deletedKey = key;
        }
    }

    private static final class FailingDeletionStorage
            extends SuccessfulDeletionStorage {

        @Override
        public void delete(StorageKey key) {
            throw new FileStorageException(
                    "injected deletion failure",
                    new IllegalStateException()
            );
        }
    }
}
