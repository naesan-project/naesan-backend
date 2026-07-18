package com.naesan.evidence.application;

import java.io.InputStream;

import com.naesan.evidence.domain.EvidenceFile;

public record DownloadedEvidenceFile(
        EvidenceFile file,
        InputStream content
) {

    public DownloadedEvidenceFile {
        if (file == null || content == null) {
            throw new IllegalArgumentException("다운로드 파일은 null일 수 없습니다.");
        }
    }
}
