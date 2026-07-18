package com.naesan.evidence.application;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class MeasuredDigestInputStream extends InputStream {
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final InputStream content;
    private final long maximumSize;
    private final MessageDigest messageDigest;
    private long size;
    private boolean completelyRead;

    MeasuredDigestInputStream(InputStream content, long maximumSize) {
        this.content = content;
        this.maximumSize = maximumSize;
        this.messageDigest = createMessageDigest();
    }

    private MessageDigest createMessageDigest() {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    @Override
    public int read() throws IOException {
        int character = content.read();
        if (character == -1) {
            completelyRead = true;
            return -1;
        }

        requireAvailableSize(1);
        messageDigest.update((byte) character);
        size++;
        return character;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int readSize = content.read(buffer, offset, length);
        if (readSize == -1) {
            completelyRead = true;
            return -1;
        }

        requireAvailableSize(readSize);
        messageDigest.update(buffer, offset, readSize);
        size += readSize;
        return readSize;
    }

    private void requireAvailableSize(int readSize) {
        if (readSize > maximumSize - size) {
            throw new EvidenceFileException(
                    EvidenceFileErrorCode.FILE_TOO_LARGE,
                    "파일 크기 제한을 초과했습니다."
            );
        }
    }

    long size() {
        requireCompletelyRead();
        return size;
    }

    String sha256() {
        requireCompletelyRead();
        return HexFormat.of().formatHex(messageDigest.digest());
    }

    private void requireCompletelyRead() {
        if (!completelyRead) {
            throw new IllegalStateException("파일을 끝까지 읽지 않았습니다.");
        }
    }
}
