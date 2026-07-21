package com.naesan.share.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import com.naesan.evidence.domain.EvidenceFileType;
import com.naesan.passport.domain.AnchorCommitment;
import com.naesan.passport.domain.AnchorCommitmentCalculator;

public final class PublicFileMatchVerifier {
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final byte[] FILE_DIGEST_PREFIX =
            "\"fileSha256\":\"".getBytes(StandardCharsets.UTF_8);
    private static final int SHA256_HEX_LENGTH = 64;

    private final AnchorCommitmentCalculator commitmentCalculator;
    private final long maximumFileSize;

    public PublicFileMatchVerifier(
            AnchorCommitmentCalculator commitmentCalculator,
            long maximumFileSize
    ) {
        this.commitmentCalculator = Objects.requireNonNull(commitmentCalculator);
        if (maximumFileSize <= 0) {
            throw new IllegalArgumentException("대조 파일 최대 크기는 0보다 커야 합니다.");
        }
        this.maximumFileSize = maximumFileSize;
    }

    public boolean matches(
            InputStream candidateFile,
            String declaredMediaType,
            PublicShareVerificationSource source
    ) {
        String candidateFileDigest = digestCandidateFile(
                candidateFile,
                declaredMediaType
        );
        byte[] candidatePayload = replaceFileDigest(
                source.canonicalPayload(),
                candidateFileDigest
        );
        String candidateSnapshotDigest = HexFormat.of()
                .formatHex(sha256(candidatePayload));
        AnchorCommitment candidateCommitment = commitmentCalculator.calculate(
                candidateSnapshotDigest,
                source.anchorSalt()
        );
        return MessageDigest.isEqual(
                candidateCommitment.commitment(),
                source.commitment()
        );
    }

    private String digestCandidateFile(
            InputStream candidateFile,
            String declaredMediaType
    ) {
        EvidenceFileType fileType = EvidenceFileType
                .findByMediaType(declaredMediaType)
                .orElseThrow(PublicShareException::unsupportedFile);
        if (candidateFile == null) {
            throw PublicShareException.emptyFile();
        }
        try {
            PushbackInputStream inspectedFile = inspectFile(candidateFile, fileType);
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] buffer = new byte[8192];
            long totalSize = 0;
            int readSize;
            while ((readSize = inspectedFile.read(buffer)) != -1) {
                totalSize += readSize;
                if (totalSize > maximumFileSize) {
                    throw PublicShareException.fileTooLarge();
                }
                digest.update(buffer, 0, readSize);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        } catch (IOException exception) {
            throw PublicShareException.fileReadFailed(exception);
        }
    }

    private PushbackInputStream inspectFile(
            InputStream candidateFile,
            EvidenceFileType fileType
    ) throws IOException {
        PushbackInputStream inspectedFile = new PushbackInputStream(
                candidateFile,
                EvidenceFileType.maximumSignatureLength()
        );
        byte[] prefix = inspectedFile.readNBytes(
                EvidenceFileType.maximumSignatureLength()
        );
        if (prefix.length == 0) {
            throw PublicShareException.emptyFile();
        }
        if (!fileType.matchesSignature(prefix)) {
            throw PublicShareException.fileTypeMismatch();
        }
        inspectedFile.unread(prefix);
        return inspectedFile;
    }

    private byte[] replaceFileDigest(byte[] canonicalPayload, String fileDigest) {
        int digestStart = indexOf(canonicalPayload, FILE_DIGEST_PREFIX)
                + FILE_DIGEST_PREFIX.length;
        if (digestStart < FILE_DIGEST_PREFIX.length
                || digestStart + SHA256_HEX_LENGTH >= canonicalPayload.length
                || canonicalPayload[digestStart + SHA256_HEX_LENGTH] != '"') {
            throw new IllegalStateException("Canonical payload의 file digest가 유효하지 않습니다.");
        }
        byte[] candidatePayload = canonicalPayload.clone();
        byte[] digestBytes = fileDigest.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(
                digestBytes,
                0,
                candidatePayload,
                digestStart,
                SHA256_HEX_LENGTH
        );
        return candidatePayload;
    }

    private int indexOf(byte[] content, byte[] target) {
        for (int start = 0; start <= content.length - target.length; start++) {
            boolean matched = true;
            for (int index = 0; index < target.length; index++) {
                if (content[start + index] != target[index]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return start;
            }
        }
        return -1;
    }

    private byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM).digest(content);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
