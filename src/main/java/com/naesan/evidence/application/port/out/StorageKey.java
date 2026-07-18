package com.naesan.evidence.application.port.out;

public record StorageKey(String value) {

    public StorageKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("파일 저장소 키는 비어 있을 수 없습니다.");
        }
    }
}
