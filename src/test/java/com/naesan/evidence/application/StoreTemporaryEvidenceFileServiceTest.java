package com.naesan.evidence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.naesan.evidence.adapter.out.storage.LocalFileStorage;
import com.naesan.evidence.domain.EvidenceFileType;

class StoreTemporaryEvidenceFileServiceTest {
    private static final byte[] JPEG_CONTENT =
            bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x01);
    private static final byte[] PNG_CONTENT =
            bytes(0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0x01);
    private static final byte[] PDF_CONTENT =
            "%PDF-1.7\nreceipt".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path storageRoot;

    @ParameterizedTest
    @MethodSource("supportedFiles")
    @DisplayName("지원하는 파일 형식을 signature 검사 후 저장한다")
    void storesSupportedFile(
            String mediaType,
            EvidenceFileType expectedFileType,
            byte[] content
    ) throws IOException {
        StoreTemporaryEvidenceFileService service = service(1024);

        StoredEvidenceFile storedFile = service.store(
                new ByteArrayInputStream(content),
                mediaType
        );

        assertThat(storedFile.fileType()).isEqualTo(expectedFileType);
        assertThat(storedFile.size()).isEqualTo(content.length);
        assertThat(storedFile.sha256()).hasSize(64);
        try (InputStream storedContent = fileStorage().open(storedFile.key())) {
            assertThat(storedContent.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("SHA-256은 서버가 읽은 파일 bytes로 계산한다")
    void calculatesSha256FromStoredContent() {
        StoreTemporaryEvidenceFileService service = service(1024);

        StoredEvidenceFile storedFile = service.store(
                new ByteArrayInputStream(PDF_CONTENT),
                "application/pdf"
        );

        assertThat(storedFile.sha256())
                .isEqualTo("d8bbdb07ec7989be913bf5074759320c"
                        + "476c70086e52b80a38f7b07d416f0371");
    }

    @Test
    @DisplayName("최대 크기와 같은 파일은 저장한다")
    void storesFileAtMaximumSize() {
        StoreTemporaryEvidenceFileService service = service(PDF_CONTENT.length);

        StoredEvidenceFile storedFile = service.store(
                new ByteArrayInputStream(PDF_CONTENT),
                "application/pdf"
        );

        assertThat(storedFile.size()).isEqualTo(PDF_CONTENT.length);
    }

    @Test
    @DisplayName("지원하지 않는 MIME 형식을 저장 전에 거절한다")
    void rejectsUnsupportedMediaType() throws IOException {
        StoreTemporaryEvidenceFileService service = service(1024);

        assertThatThrownBy(() -> service.store(
                new ByteArrayInputStream(PDF_CONTENT),
                "image/gif"
        ))
                .isInstanceOf(EvidenceFileException.class)
                .extracting(exception -> ((EvidenceFileException) exception).code())
                .isEqualTo(EvidenceFileErrorCode.UNSUPPORTED_FILE_TYPE);
        assertThat(storedFileCount()).isZero();
    }

    @Test
    @DisplayName("선언한 MIME과 실제 file signature가 다르면 저장하지 않는다")
    void rejectsMismatchedFileType() throws IOException {
        StoreTemporaryEvidenceFileService service = service(1024);

        assertThatThrownBy(() -> service.store(
                new ByteArrayInputStream(PDF_CONTENT),
                "image/png"
        ))
                .isInstanceOf(EvidenceFileException.class)
                .extracting(exception -> ((EvidenceFileException) exception).code())
                .isEqualTo(EvidenceFileErrorCode.FILE_TYPE_MISMATCH);
        assertThat(storedFileCount()).isZero();
    }

    @Test
    @DisplayName("빈 파일을 저장 전에 거절한다")
    void rejectsEmptyFile() throws IOException {
        StoreTemporaryEvidenceFileService service = service(1024);

        assertThatThrownBy(() -> service.store(
                InputStream.nullInputStream(),
                "application/pdf"
        ))
                .isInstanceOf(EvidenceFileException.class)
                .extracting(exception -> ((EvidenceFileException) exception).code())
                .isEqualTo(EvidenceFileErrorCode.EMPTY_FILE);
        assertThat(storedFileCount()).isZero();
    }

    @Test
    @DisplayName("파일을 읽지 못하면 원인을 구분해 반환한다")
    void reportsFileReadFailure() throws IOException {
        StoreTemporaryEvidenceFileService service = service(1024);
        InputStream unreadableContent = new InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("injected read failure");
            }
        };

        assertThatThrownBy(() -> service.store(
                unreadableContent,
                "application/pdf"
        ))
                .isInstanceOf(EvidenceFileException.class)
                .extracting(exception -> ((EvidenceFileException) exception).code())
                .isEqualTo(EvidenceFileErrorCode.FILE_READ_FAILED);
        assertThat(storedFileCount()).isZero();
    }

    @Test
    @DisplayName("크기 제한을 넘으면 부분 파일을 제거한다")
    void rejectsOversizedFileAndRemovesPartialObject() throws IOException {
        StoreTemporaryEvidenceFileService service = service(PDF_CONTENT.length - 1);

        assertThatThrownBy(() -> service.store(
                new ByteArrayInputStream(PDF_CONTENT),
                "application/pdf"
        ))
                .isInstanceOf(EvidenceFileException.class)
                .extracting(exception -> ((EvidenceFileException) exception).code())
                .isEqualTo(EvidenceFileErrorCode.FILE_TOO_LARGE);
        assertThat(storedFileCount()).isZero();
    }

    private StoreTemporaryEvidenceFileService service(long maximumFileSize) {
        return new StoreTemporaryEvidenceFileService(
                fileStorage(),
                maximumFileSize
        );
    }

    private LocalFileStorage fileStorage() {
        return new LocalFileStorage(storageRoot);
    }

    private long storedFileCount() throws IOException {
        try (Stream<Path> storedPaths = Files.walk(storageRoot)) {
            return storedPaths.filter(Files::isRegularFile).count();
        }
    }

    private static Stream<Arguments> supportedFiles() {
        return Stream.of(
                Arguments.of("image/jpeg", EvidenceFileType.JPEG, JPEG_CONTENT),
                Arguments.of("image/png", EvidenceFileType.PNG, PNG_CONTENT),
                Arguments.of("application/pdf", EvidenceFileType.PDF, PDF_CONTENT)
        );
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            bytes[index] = (byte) values[index];
        }
        return bytes;
    }
}
