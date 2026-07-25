package com.naesan.evidence.adapter.out.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.naesan.evidence.application.port.out.FileStorage;
import com.naesan.evidence.application.port.out.FileStorageException;
import com.naesan.evidence.application.port.out.StoredObjectMetadata;
import com.naesan.evidence.domain.StorageKey;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

public final class S3FileStorage implements FileStorage {
    private static final String TEMPORARY_DIRECTORY = "temporary";
    private static final String PERMANENT_DIRECTORY = "permanent";
    private static final int MULTIPART_PART_SIZE = 5 * 1024 * 1024;

    private final S3Client s3Client;
    private final String bucket;
    private final ServerSideEncryption serverSideEncryption;

    public S3FileStorage(
            S3Client s3Client,
            String bucket,
            ServerSideEncryption serverSideEncryption
    ) {
        this.s3Client = Objects.requireNonNull(s3Client);
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("S3 bucket은 비어 있을 수 없습니다.");
        }
        this.bucket = bucket;
        this.serverSideEncryption = serverSideEncryption;
    }

    @Override
    public StorageKey storeTemporary(InputStream content) {
        Objects.requireNonNull(content);
        StorageKey key = createKey(TEMPORARY_DIRECTORY);
        CreateMultipartUploadResponse upload = createMultipartUpload(key);

        try {
            List<CompletedPart> parts = uploadParts(key, upload.uploadId(), content);
            completeMultipartUpload(key, upload.uploadId(), parts);
            return key;
        } catch (IOException | RuntimeException exception) {
            abortMultipartUpload(key, upload.uploadId(), exception);
            throw new FileStorageException("임시 파일을 저장하지 못했습니다.", exception);
        }
    }

    private StorageKey createKey(String directory) {
        return new StorageKey(directory + "/" + UUID.randomUUID());
    }

    private CreateMultipartUploadResponse createMultipartUpload(StorageKey key) {
        try {
            CreateMultipartUploadRequest.Builder request =
                    CreateMultipartUploadRequest.builder()
                            .bucket(bucket)
                            .key(key.value());
            if (serverSideEncryption != null) {
                request.serverSideEncryption(serverSideEncryption);
            }
            return s3Client.createMultipartUpload(
                    request.build()
            );
        } catch (RuntimeException exception) {
            throw new FileStorageException("임시 파일 저장을 시작하지 못했습니다.", exception);
        }
    }

    private List<CompletedPart> uploadParts(
            StorageKey key,
            String uploadId,
            InputStream content
    ) throws IOException {
        List<CompletedPart> completedParts = new ArrayList<>();
        byte[] buffer = new byte[MULTIPART_PART_SIZE];
        int partNumber = 1;
        int partSize;

        while ((partSize = content.readNBytes(buffer, 0, buffer.length)) > 0) {
            completedParts.add(uploadPart(
                    key,
                    uploadId,
                    partNumber,
                    buffer,
                    partSize
            ));
            partNumber++;
        }
        if (completedParts.isEmpty()) {
            completedParts.add(uploadPart(
                    key,
                    uploadId,
                    partNumber,
                    new byte[0],
                    0
            ));
        }
        return completedParts;
    }

    private CompletedPart uploadPart(
            StorageKey key,
            String uploadId,
            int partNumber,
            byte[] content,
            int contentLength
    ) {
        var response = s3Client.uploadPart(
                UploadPartRequest.builder()
                        .bucket(bucket)
                        .key(key.value())
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .contentLength((long) contentLength)
                        .build(),
                RequestBody.fromInputStream(
                        new ByteArrayInputStream(content, 0, contentLength),
                        contentLength
                )
        );
        return CompletedPart.builder()
                .partNumber(partNumber)
                .eTag(response.eTag())
                .build();
    }

    private void completeMultipartUpload(
            StorageKey key,
            String uploadId,
            List<CompletedPart> parts
    ) {
        s3Client.completeMultipartUpload(request -> request
                .bucket(bucket)
                .key(key.value())
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder()
                        .parts(parts)
                        .build())
        );
    }

    private void abortMultipartUpload(
            StorageKey key,
            String uploadId,
            Throwable storageFailure
    ) {
        try {
            s3Client.abortMultipartUpload(
                    AbortMultipartUploadRequest.builder()
                            .bucket(bucket)
                            .key(key.value())
                            .uploadId(uploadId)
                            .build()
            );
        } catch (RuntimeException cleanupFailure) {
            storageFailure.addSuppressed(cleanupFailure);
        }
    }

    @Override
    public InputStream open(StorageKey key) {
        requireManagedKey(key);
        try {
            return s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key.value())
                            .build()
            );
        } catch (NoSuchKeyException exception) {
            throw new FileStorageException("저장된 파일을 찾지 못했습니다.", exception);
        } catch (RuntimeException exception) {
            throw new FileStorageException("저장된 파일을 열지 못했습니다.", exception);
        }
    }

    private void requireManagedKey(StorageKey key) {
        Objects.requireNonNull(key);
        String value = key.value();
        if (!value.startsWith(TEMPORARY_DIRECTORY + "/")
                && !value.startsWith(PERMANENT_DIRECTORY + "/")) {
            throw new IllegalArgumentException("S3 저장소 범위를 벗어난 키입니다.");
        }
    }

    @Override
    public StorageKey promote(StorageKey temporaryKey) {
        requireDirectory(temporaryKey, TEMPORARY_DIRECTORY);
        StorageKey permanentKey = createKey(PERMANENT_DIRECTORY);
        try {
            CopyObjectRequest.Builder request = CopyObjectRequest.builder()
                    .bucket(bucket)
                    .copySource(bucket + "/" + temporaryKey.value())
                    .key(permanentKey.value());
            if (serverSideEncryption != null) {
                request.serverSideEncryption(serverSideEncryption);
            }
            s3Client.copyObject(
                    request.build()
            );
            return permanentKey;
        } catch (RuntimeException exception) {
            throw new FileStorageException("임시 파일을 승격하지 못했습니다.", exception);
        }
    }

    private void requireDirectory(StorageKey key, String directory) {
        Objects.requireNonNull(key);
        if (!key.value().startsWith(directory + "/")) {
            throw new IllegalArgumentException("S3 저장소 key 상태가 올바르지 않습니다.");
        }
    }

    @Override
    public List<StoredObjectMetadata> listTemporaryObjects() {
        return listObjects(TEMPORARY_DIRECTORY);
    }

    private List<StoredObjectMetadata> listObjects(String directory) {
        try {
            return s3Client.listObjectsV2Paginator(
                            ListObjectsV2Request.builder()
                                    .bucket(bucket)
                                    .prefix(directory + "/")
                                    .build()
                    )
                    .stream()
                    .flatMap(response -> response.contents().stream())
                    .map(object -> new StoredObjectMetadata(
                            new StorageKey(object.key()),
                            object.lastModified()
                    ))
                    .toList();
        } catch (S3Exception exception) {
            throw new FileStorageException("저장 파일 목록을 조회하지 못했습니다.", exception);
        }
    }

    @Override
    public List<StoredObjectMetadata> listPermanentObjects() {
        return listObjects(PERMANENT_DIRECTORY);
    }

    @Override
    public void delete(StorageKey key) {
        requireManagedKey(key);
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(key.value())
                            .build()
            );
        } catch (RuntimeException exception) {
            throw new FileStorageException("저장된 파일을 삭제하지 못했습니다.", exception);
        }
    }
}
