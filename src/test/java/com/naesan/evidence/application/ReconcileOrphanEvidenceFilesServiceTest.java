package com.naesan.evidence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.naesan.evidence.adapter.out.storage.LocalFileStorage;
import com.naesan.evidence.application.port.out.EvidenceFileRepository;
import com.naesan.evidence.application.port.out.FileStorageException;
import com.naesan.evidence.domain.EvidenceFile;
import com.naesan.evidence.domain.EvidenceFileState;
import com.naesan.evidence.domain.StorageKey;

class ReconcileOrphanEvidenceFilesServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");
    private static final byte[] FILE_CONTENT =
            "private evidence".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path rootDirectory;

    @Test
    @DisplayName("유예 시간이 지난 미참조 영구 파일만 삭제한다")
    void deletesOnlyOldUnreferencedPermanentObjects() throws Exception {
        LocalFileStorage fileStorage = new LocalFileStorage(rootDirectory);
        StorageKey oldOrphan = permanentObject(fileStorage);
        StorageKey referencedObject = permanentObject(fileStorage);
        StorageKey recentOrphan = permanentObject(fileStorage);
        setLastModifiedAt(oldOrphan, NOW.minus(Duration.ofHours(2)));
        setLastModifiedAt(referencedObject, NOW.minus(Duration.ofHours(2)));
        setLastModifiedAt(recentOrphan, NOW.minus(Duration.ofMinutes(30)));
        ReconcileOrphanEvidenceFilesService service =
                new ReconcileOrphanEvidenceFilesService(
                        new ReferencedKeyRepository(Set.of(referencedObject)),
                        fileStorage,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Duration.ofHours(1)
                );

        OrphanReconciliationResult result = service.reconcile();

        assertThat(result).isEqualTo(new OrphanReconciliationResult(3, 1, 0));
        assertThatThrownBy(() -> fileStorage.open(oldOrphan))
                .isInstanceOf(FileStorageException.class);
        assertThat(fileStorage.open(referencedObject)).isNotNull();
        assertThat(fileStorage.open(recentOrphan)).isNotNull();
    }

    private StorageKey permanentObject(LocalFileStorage fileStorage) {
        StorageKey temporaryKey = fileStorage.storeTemporary(
                new ByteArrayInputStream(FILE_CONTENT)
        );
        return fileStorage.promote(temporaryKey);
    }

    private void setLastModifiedAt(StorageKey key, Instant lastModifiedAt)
            throws Exception {
        Files.setLastModifiedTime(
                rootDirectory.resolve(key.value()),
                FileTime.from(lastModifiedAt)
        );
    }

    private record ReferencedKeyRepository(
            Set<StorageKey> referencedKeys
    ) implements EvidenceFileRepository {

        @Override
        public void save(EvidenceFile evidenceFile) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(EvidenceFile evidenceFile) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<StorageKey> findAllObjectKeys() {
            return referencedKeys;
        }

        @Override
        public List<EvidenceFile> findAllByState(EvidenceFileState state) {
            return List.of();
        }

        @Override
        public Optional<EvidenceFile> findByEvidenceId(UUID evidenceId) {
            return Optional.empty();
        }
    }
}
