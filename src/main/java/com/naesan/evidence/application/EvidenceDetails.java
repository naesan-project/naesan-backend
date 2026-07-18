package com.naesan.evidence.application;

import java.util.Optional;

import com.naesan.evidence.domain.EvidenceFile;
import com.naesan.evidence.domain.PurchaseEvidence;

public record EvidenceDetails(
        PurchaseEvidence evidence,
        Optional<EvidenceFile> file
) {

    public EvidenceDetails {
        if (evidence == null || file == null) {
            throw new IllegalArgumentException("구매 증빙 상세 정보는 null일 수 없습니다.");
        }
    }
}
