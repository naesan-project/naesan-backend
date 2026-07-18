package com.naesan.evidence.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Objects;

import com.naesan.evidence.application.port.out.FileStorage;
import com.naesan.evidence.domain.EvidenceFileType;
import com.naesan.evidence.domain.StorageKey;

public final class StoreTemporaryEvidenceFileService {
    private final FileStorage fileStorage;
    private final long maximumFileSize;

    public StoreTemporaryEvidenceFileService(
            FileStorage fileStorage,
            long maximumFileSize
    ) {
        this.fileStorage = Objects.requireNonNull(fileStorage);
        if (maximumFileSize <= 0) {
            throw new IllegalArgumentException("최대 파일 크기는 0보다 커야 합니다.");
        }
        this.maximumFileSize = maximumFileSize;
    }

    public StoredEvidenceFile store(InputStream content, String declaredMediaType) {
        EvidenceFileType fileType = requireSupportedFileType(declaredMediaType);
        PushbackInputStream inspectedContent = inspectFileType(content, fileType);
        MeasuredDigestInputStream measuredContent =
                new MeasuredDigestInputStream(inspectedContent, maximumFileSize);

        StorageKey key = fileStorage.storeTemporary(measuredContent);

        return new StoredEvidenceFile(
                key,
                fileType,
                measuredContent.size(),
                measuredContent.sha256()
        );
    }

    private EvidenceFileType requireSupportedFileType(String declaredMediaType) {
        return EvidenceFileType.findByMediaType(declaredMediaType)
                .orElseThrow(() -> new EvidenceFileException(
                        EvidenceFileErrorCode.UNSUPPORTED_FILE_TYPE,
                        "JPEG, PNG, PDF 파일만 업로드할 수 있습니다."
                ));
    }

    private PushbackInputStream inspectFileType(
            InputStream content,
            EvidenceFileType fileType
    ) {
        if (content == null) {
            throw new EvidenceFileException(
                    EvidenceFileErrorCode.EMPTY_FILE,
                    "업로드할 파일이 비어 있습니다."
            );
        }

        PushbackInputStream inspectedContent = new PushbackInputStream(
                content,
                EvidenceFileType.maximumSignatureLength()
        );

        try {
            byte[] filePrefix = inspectedContent.readNBytes(
                    EvidenceFileType.maximumSignatureLength()
            );
            requireFileSignature(fileType, filePrefix);
            inspectedContent.unread(filePrefix);
            return inspectedContent;
        } catch (IOException exception) {
            throw new EvidenceFileException(
                    EvidenceFileErrorCode.FILE_READ_FAILED,
                    "업로드 파일을 읽지 못했습니다.",
                    exception
            );
        }
    }

    private void requireFileSignature(
            EvidenceFileType fileType,
            byte[] filePrefix
    ) {
        if (filePrefix.length == 0) {
            throw new EvidenceFileException(
                    EvidenceFileErrorCode.EMPTY_FILE,
                    "업로드할 파일이 비어 있습니다."
            );
        }
        if (!fileType.matchesSignature(filePrefix)) {
            throw new EvidenceFileException(
                    EvidenceFileErrorCode.FILE_TYPE_MISMATCH,
                    "파일 내용이 선언된 형식과 일치하지 않습니다."
            );
        }
    }
}
