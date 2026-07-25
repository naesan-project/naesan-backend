package com.naesan.evidence.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.naesan.evidence.application.port.out.FileStorageException;
import com.naesan.evidence.domain.StorageKey;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListMultipartUploadsRequest;

@Testcontainers
class S3FileStorageTest {
    private static final int MULTIPART_FILE_SIZE = 6 * 1024 * 1024;
    private static final String BUCKET = "naesan-private";
    private static final byte[] FILE_CONTENT =
            "private evidence".getBytes(StandardCharsets.UTF_8);

    @Container
    private static final MinIOContainer MINIO =
            new MinIOContainer("minio/minio:RELEASE.2025-04-22T22-12-26Z");

    private static S3Client s3Client;
    private static S3FileStorage fileStorage;

    @BeforeAll
    static void prepareStorage() {
        s3Client = S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                MINIO.getUserName(),
                                MINIO.getPassword()
                        )
                ))
                .endpointOverride(java.net.URI.create(MINIO.getS3URL()))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .build();
        s3Client.createBucket(CreateBucketRequest.builder()
                .bucket(BUCKET)
                .build());
        fileStorage = new S3FileStorage(s3Client, BUCKET, null);
    }

    @AfterAll
    static void closeClient() {
        s3Client.close();
    }

    @Test
    @DisplayName("S3-compatible private bucket에 임시 객체를 저장하고 다시 읽는다")
    void storesAndOpensTemporaryObject() throws IOException {
        StorageKey key = fileStorage.storeTemporary(content());

        assertThat(key.value()).startsWith("temporary/");
        try (InputStream storedContent = fileStorage.open(key)) {
            assertThat(storedContent.readAllBytes()).isEqualTo(FILE_CONTENT);
        }
    }

    @Test
    @DisplayName("길이를 모르는 5 MiB 초과 stream을 multipart로 저장한다")
    void storesMultipartStream() throws IOException {
        byte[] content = new byte[MULTIPART_FILE_SIZE];
        content[0] = 1;
        content[content.length - 1] = 2;

        StorageKey key = fileStorage.storeTemporary(
                new ByteArrayInputStream(content)
        );

        try (InputStream storedContent = fileStorage.open(key)) {
            assertThat(storedContent.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("임시 객체를 영구 key로 복사하고 prefix별로 조회한다")
    void promotesAndListsObjects() {
        StorageKey temporaryKey = fileStorage.storeTemporary(content());

        StorageKey permanentKey = fileStorage.promote(temporaryKey);

        assertThat(fileStorage.listTemporaryObjects())
                .extracting(object -> object.key().value())
                .contains(temporaryKey.value());
        assertThat(fileStorage.listPermanentObjects())
                .extracting(object -> object.key().value())
                .contains(permanentKey.value());
    }

    @Test
    @DisplayName("S3 객체 삭제는 이미 삭제된 경우에도 성공한다")
    void deletesObjectIdempotently() {
        StorageKey key = fileStorage.storeTemporary(content());

        fileStorage.delete(key);
        fileStorage.delete(key);

        assertThatThrownBy(() -> fileStorage.open(key))
                .isInstanceOf(FileStorageException.class);
    }

    @Test
    @DisplayName("stream 실패 시 multipart upload와 부분 객체를 남기지 않는다")
    void abortsMultipartUploadAfterStreamFailure() {
        assertThatThrownBy(() -> fileStorage.storeTemporary(failingContent()))
                .isInstanceOf(FileStorageException.class)
                .hasMessage("임시 파일을 저장하지 못했습니다.");

        assertThat(s3Client.listMultipartUploads(
                ListMultipartUploadsRequest.builder()
                        .bucket(BUCKET)
                        .build()
        ).uploads()).isEmpty();
    }

    @Test
    @DisplayName("관리 prefix 밖의 S3 key 접근을 거절한다")
    void rejectsKeyOutsideManagedPrefixes() {
        assertThatThrownBy(() -> fileStorage.open(new StorageKey("outside/file")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("S3 저장소 범위를 벗어난 키입니다.");
    }

    private static ByteArrayInputStream content() {
        return new ByteArrayInputStream(FILE_CONTENT);
    }

    private InputStream failingContent() {
        return new InputStream() {
            private int readCount;

            @Override
            public int read() throws IOException {
                if (readCount < FILE_CONTENT.length / 2) {
                    return FILE_CONTENT[readCount++];
                }
                throw new IOException("injected storage failure");
            }
        };
    }
}
