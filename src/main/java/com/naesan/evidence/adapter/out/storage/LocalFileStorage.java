package com.naesan.evidence.adapter.out.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

import com.naesan.evidence.application.port.out.FileStorage;
import com.naesan.evidence.application.port.out.FileStorageException;
import com.naesan.evidence.application.port.out.StorageKey;

public final class LocalFileStorage implements FileStorage {
    private static final String TEMPORARY_DIRECTORY = "temporary";

    private final Path rootDirectory;

    public LocalFileStorage(Path rootDirectory) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory)
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public StorageKey storeTemporary(InputStream content) {
        Objects.requireNonNull(content);

        StorageKey key = createTemporaryKey();
        Path objectPath = resolveObjectPath(key);

        try {
            Files.createDirectories(objectPath.getParent());
            Files.copy(content, objectPath);
            return key;
        } catch (IOException exception) {
            deletePartiallyStoredObject(objectPath, exception);
            throw new FileStorageException("임시 파일을 저장하지 못했습니다.", exception);
        }
    }

    private StorageKey createTemporaryKey() {
        return new StorageKey(TEMPORARY_DIRECTORY + "/" + UUID.randomUUID());
    }

    private void deletePartiallyStoredObject(Path objectPath, IOException storageFailure) {
        try {
            Files.deleteIfExists(objectPath);
        } catch (IOException cleanupFailure) {
            storageFailure.addSuppressed(cleanupFailure);
        }
    }

    @Override
    public InputStream open(StorageKey key) {
        Path objectPath = resolveObjectPath(key);

        try {
            return Files.newInputStream(objectPath);
        } catch (IOException exception) {
            throw new FileStorageException("저장된 파일을 열지 못했습니다.", exception);
        }
    }

    @Override
    public void delete(StorageKey key) {
        Path objectPath = resolveObjectPath(key);

        try {
            Files.deleteIfExists(objectPath);
        } catch (IOException exception) {
            throw new FileStorageException("저장된 파일을 삭제하지 못했습니다.", exception);
        }
    }

    private Path resolveObjectPath(StorageKey key) {
        Objects.requireNonNull(key);

        Path objectPath = rootDirectory.resolve(key.value()).normalize();
        if (!objectPath.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("파일 저장소 범위를 벗어난 키입니다.");
        }
        return objectPath;
    }
}
