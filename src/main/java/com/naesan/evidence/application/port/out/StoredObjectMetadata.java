package com.naesan.evidence.application.port.out;

import java.time.Instant;

import com.naesan.evidence.domain.StorageKey;

public record StoredObjectMetadata(
        StorageKey key,
        Instant lastModifiedAt
) {

    public StoredObjectMetadata {
        if (key == null || lastModifiedAt == null) {
            throw new IllegalArgumentException("저장 객체 정보는 null일 수 없습니다.");
        }
    }
}
