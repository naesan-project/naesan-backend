package com.naesan.evidence.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.naesan.evidence.application.port.out.FileStorageException;
import com.naesan.evidence.domain.StorageKey;

class LocalFileStorageTest {
    private static final byte[] FILE_CONTENT =
            "private evidence".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path rootDirectory;

    @Test
    @DisplayName("파일을 무작위 임시 키로 저장하고 다시 읽는다")
    void storesAndOpensTemporaryObject() throws IOException {
        LocalFileStorage fileStorage = fileStorage();

        StorageKey key = fileStorage.storeTemporary(content());

        assertThat(key.value())
                .matches("temporary/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}"
                        + "-[0-9a-f]{4}-[0-9a-f]{12}");
        try (InputStream storedContent = fileStorage.open(key)) {
            assertThat(storedContent.readAllBytes()).isEqualTo(FILE_CONTENT);
        }
    }

    @Test
    @DisplayName("같은 내용도 서로 다른 저장소 키를 사용한다")
    void doesNotUseContentAddressedKeys() {
        LocalFileStorage fileStorage = fileStorage();

        StorageKey firstKey = fileStorage.storeTemporary(content());
        StorageKey secondKey = fileStorage.storeTemporary(content());

        assertThat(firstKey).isNotEqualTo(secondKey);
    }

    @Test
    @DisplayName("임시 파일 삭제는 이미 삭제된 경우에도 성공한다")
    void deletesTemporaryObjectIdempotently() {
        LocalFileStorage fileStorage = fileStorage();
        StorageKey key = fileStorage.storeTemporary(content());

        fileStorage.delete(key);
        fileStorage.delete(key);

        assertThatThrownBy(() -> fileStorage.open(key))
                .isInstanceOf(FileStorageException.class)
                .hasMessage("저장된 파일을 열지 못했습니다.");
    }

    @Test
    @DisplayName("저장 도중 실패하면 부분 파일을 남기지 않는다")
    void removesPartiallyStoredObjectAfterFailure() throws IOException {
        LocalFileStorage fileStorage = fileStorage();

        assertThatThrownBy(() -> fileStorage.storeTemporary(failingContent()))
                .isInstanceOf(FileStorageException.class)
                .hasMessage("임시 파일을 저장하지 못했습니다.");

        try (var storedPaths = Files.walk(rootDirectory)) {
            assertThat(storedPaths.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    @DisplayName("저장소 경계를 벗어나는 키를 거절한다")
    void rejectsKeyOutsideStorageRoot() {
        LocalFileStorage fileStorage = fileStorage();
        StorageKey outsideKey = new StorageKey("../outside");

        assertThatThrownBy(() -> fileStorage.open(outsideKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("파일 저장소 범위를 벗어난 키입니다.");
    }

    private LocalFileStorage fileStorage() {
        return new LocalFileStorage(rootDirectory);
    }

    private ByteArrayInputStream content() {
        return new ByteArrayInputStream(FILE_CONTENT);
    }

    private InputStream failingContent() {
        return new InputStream() {
            private int readCount;

            @Override
            public int read() throws IOException {
                if (readCount++ < FILE_CONTENT.length / 2) {
                    return FILE_CONTENT[readCount];
                }
                throw new IOException("injected storage failure");
            }
        };
    }
}
