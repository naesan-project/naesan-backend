package com.naesan.evidence.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum EvidenceFileType {
    JPEG("image/jpeg", "jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", "png", new byte[] {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1A, '\n'
    }),
    PDF("application/pdf", "pdf", new byte[] {'%', 'P', 'D', 'F', '-'});

    private final String mediaType;
    private final String fileExtension;
    private final byte[] signature;

    EvidenceFileType(String mediaType, String fileExtension, byte[] signature) {
        this.mediaType = mediaType;
        this.fileExtension = fileExtension;
        this.signature = signature;
    }

    public static Optional<EvidenceFileType> findByMediaType(String mediaType) {
        if (mediaType == null) {
            return Optional.empty();
        }

        String normalizedMediaType = mediaType.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(fileType -> fileType.mediaType.equals(normalizedMediaType))
                .findFirst();
    }

    public static int maximumSignatureLength() {
        return Arrays.stream(values())
                .mapToInt(fileType -> fileType.signature.length)
                .max()
                .orElseThrow();
    }

    public boolean matchesSignature(byte[] filePrefix) {
        if (filePrefix.length < signature.length) {
            return false;
        }

        for (int index = 0; index < signature.length; index++) {
            if (filePrefix[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    public String mediaType() {
        return mediaType;
    }

    public String fileExtension() {
        return fileExtension;
    }
}
